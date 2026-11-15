package com.drama.app.platform

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatAirbagDeployedListener
import com.drama.app.core.Gear
import com.drama.app.core.HarshKind
import com.drama.app.core.RouteHint

private const val TAG = "DramaSignals"

const val HARSH_ACCEL_KPH_PER_S = 10f
const val HARSH_BRAKE_KPH_PER_S = 12f

class HarshDetector(private val onHarsh: (HarshKind) -> Unit) {
    private var last: Pair<Long, Float>? = null

    fun onSpeed(timestampMs: Long, kph: Float) {
        val previous = last
        last = timestampMs to kph
        if (previous == null) return
        val dt = (timestampMs - previous.first) / 1000f
        if (dt < 0.2f || dt > 3f) return
        val rate = (kph - previous.second) / dt
        when {
            rate >= HARSH_ACCEL_KPH_PER_S -> onHarsh(HarshKind.ACCEL)
            rate <= -HARSH_BRAKE_KPH_PER_S -> onHarsh(HarshKind.BRAKE)
        }
    }
}

class VehicleSignals(
    context: Context,
    private val onGear: (Gear) -> Unit,
    private val onAirbag: () -> Unit,
    onHarsh: (HarshKind) -> Unit,
    private val onUxRestricted: (Boolean) -> Unit,
) {
    private val vehicle = Vehicle(context)
    private val car: Car? = runCatching { Car.createCar(context) }.getOrNull()
    private val harsh = HarshDetector(onHarsh)
    private val driverSeats = listOf(SeatArea(SeatValue.ROW_1_LEFT))
    private var speedRegistered = false

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            when (gear) {
                VehicleGear.GEAR_PARK -> onGear(Gear.P)
                VehicleGear.GEAR_DRIVE -> onGear(Gear.D)
                VehicleGear.GEAR_REVERSE -> onGear(Gear.R)
                VehicleGear.GEAR_NEUTRAL -> onGear(Gear.N)
                else -> Unit
            }
        }
        override fun onFailed(e: Exception) = Log.w(TAG, "gear", e).let { }
    }

    private val airbagListener = object : SeatAirbagDeployedListener {
        override fun onSeatAirbagDeployUpdated(deployed: Boolean?) {
            if (deployed == true) onAirbag()
        }
        override fun onFailed(e: Exception) = Log.w(TAG, "airbag", e).let { }
    }

    private val speedCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val metersPerSecond = value.value as? Float ?: return
            harsh.onSpeed(value.timestamp / 1_000_000, metersPerSecond * 3.6f)
        }
        override fun onErrorEvent(propertyId: Int, zone: Int) = Unit
    }

    private val uxListener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener {
        onUxRestricted(it.isRequiresDistractionOptimization)
    }

    fun start() {
        runCatching { vehicle.initialize() }.onFailure { Log.w(TAG, "Pleos Vehicle unavailable", it) }
        runCatching { driverSeats.forEach { vehicle.getSafety().registerSeatAirbagDeployed(it, airbagListener) } }
            .onFailure { Log.w(TAG, "airbag listener unavailable", it) }
        runCatching { vehicle.getGear().registerGearSelection(gearListener) }.onFailure { Log.w(TAG, "gear listener unavailable", it) }
        ensureSpeed()
        runCatching {
            val ux = car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
            ux?.registerListener(uxListener)
            ux?.currentCarUxRestrictions?.let { onUxRestricted(it.isRequiresDistractionOptimization) }
        }.onFailure { Log.w(TAG, "Car service unavailable", it) }
    }

    fun ensureSpeed() {
        if (speedRegistered) return
        runCatching {
            val props = car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
            speedRegistered = props?.registerCallback(speedCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL) == true
            if (!speedRegistered) Log.w(TAG, "vehicle speed unavailable; harsh driving detection off until permission is granted")
        }.onFailure { Log.w(TAG, "speed registration failed", it) }
    }

    fun stop() {
        runCatching {
            vehicle.getGear().unregisterGearSelection(gearListener)
            driverSeats.forEach { vehicle.getSafety().unregisterSeatAirbagDeployed(it, airbagListener) }
            vehicle.release()
        }
        runCatching {
            (car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager)?.unregisterCallback(speedCallback)
            (car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager)?.unregisterListener()
            car?.disconnect()
        }
    }
}

const val CONGESTION_MIN_SECONDS = 180L
const val GUIDANCE_MIN_INTERVAL_MS = 45_000L

class NaviSignals(
    context: Context,
    private val clock: () -> Long,
    private val onEtaMinutes: (Double) -> Unit,
    private val onCongestion: () -> Unit,
    private val onGuidance: (String) -> Unit,
    private val onArrived: () -> Unit,
) {
    private val navi = NaviHelper(context)
    private var jammed = false
    private var routeActive = false
    private var lastGuidance = ""
    private var lastGuidanceAt = 0L

    private val listener = object : NaviHelperEventListener {
        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            if (destinationInfo.remainTime > 0) onEtaMinutes(destinationInfo.remainTime / 60.0)
        }

        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
            val seconds = routeStateInfo.trafficStatus?.summary?.totalCongestionTime ?: 0L
            val nowJammed = seconds >= CONGESTION_MIN_SECONDS
            if (nowJammed && !jammed) onCongestion()
            jammed = nowJammed
        }

        override fun onTBTInfo(tbtInfoList: List<TBTInfo>) {
            val next = tbtInfoList.firstOrNull()?.description?.takeIf { it.isNotBlank() } ?: return
            val now = clock()
            if (next == lastGuidance || now - lastGuidanceAt < GUIDANCE_MIN_INTERVAL_MS) return
            lastGuidance = next
            lastGuidanceAt = now
            onGuidance(next)
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) = onArrived()

        override fun onNavigationStatus(status: NavigationStatus) {
            routeActive = status == NavigationStatus.ROUTE_DRIVE
        }
    }

    fun start() {
        runCatching {
            navi.initialize()
            navi.addListener(listener)
            navi.getDestinationInfo()
            navi.getRouteStateInfo()
        }.onFailure { Log.w(TAG, "NaviHelper unavailable", it) }
    }

    fun poll() {
        runCatching {
            navi.getDestinationInfo()
            navi.getRouteStateInfo()
            navi.getTBTInfo()
        }
    }

    fun reroute(prefer: RouteHint) {
        if (prefer != RouteHint.DETOUR || !routeActive) return
        runCatching {
            navi.changeRouteOption(RouteOption.SHORT_TIME)
            navi.requestReRoute()
        }.onFailure { Log.w(TAG, "reroute failed", it) }
    }

    fun stop() {
        runCatching {
            navi.removeListener(listener)
            navi.release()
        }
    }
}
