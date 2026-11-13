# 드라마 (drama)

주행 시간을 한 편의 이야기로 바꾸는 Pleos Connect 차량용 시나리오 앱.

내비가 알려주는 남은 시간에 맞춰 이야기 분량을 조절합니다. 급제동, 정체, 경로 안내 같은 실제 주행 신호는 캐릭터의 대사와 사건이 됩니다. 도착 1분 전에는 소결말과 함께 자동 저장되고, 다음 운전에서 이어집니다.

## APK

- 위치: [`apk/`](apk/) 폴더의 `*_release.apk` (서명된 release 빌드)
- 다시 만들기: `android/scripts/release.sh <팀명>`. 단위 테스트를 돌린 뒤 release APK를 빌드해 `apk/<팀명>_드라마_release.apk`로 복사합니다.

## 테스트 환경

| 항목 | 값 |
|---|---|
| 에뮬레이터 | Pleos Connect v2.1.0 (Android 14, API 34, arm64-v8a) |
| 화면 | 2560 x 1440, mdpi. 앱은 우측 메인 영역에 가로 모드로 표시됩니다 |
| 빌드 | JDK 17, Android Gradle Plugin 8.7.3, Gradle 8.11.1, Kotlin 2.0.21, compileSdk 34, minSdk 28 |
| 호스트 | macOS (Apple Silicon) |

## 실행 방법

1. Android Studio SDK Manager에서 Pleos Connect System Image 업데이트 사이트를 추가하고 v2.1.0 이미지를 설치합니다. 그다음 Automotive AVD를 만들어 부팅합니다. 자세한 방법은 Pleos 문서의 에뮬레이터 설정 가이드를 따릅니다.
2. APK를 설치하고 권한을 함께 허용합니다.
   ```sh
   adb install -r -g apk/*_release.apk
   adb shell am start -n com.drama.app/.MainActivity
   ```
3. 스토리를 고르고 새로 시작을 누릅니다. 소요 시간을 고른 뒤 주행 시작을 누르면 됩니다.

에뮬레이터에는 실제 주행이 없습니다. 그래서 **시연 모드**가 기본으로 켜져 있습니다.

- 남은 시간이 10배속으로 줄어듭니다. 선택지 대기 시간과 반응 간격은 실제 시간 그대로입니다.
- 화면 하단의 차량 신호 패널로 급가속, 급제동, 정체, 경로 안내, 기어 P, 에어백 전개를 발생시킬 수 있습니다.
- 시연 모드를 끄면 남은 시간은 Pleos NaviHelper의 목적지 남은 시간으로 바뀝니다.

### 빌드

```sh
cd android
./gradlew :app:testReleaseUnitTest :app:assembleRelease
```

`local.properties`에 `sdk.dir`가 필요합니다. Pleos SDK는 공개 저장소(`nexus-playground.pleos.ai`)에서 받아옵니다.

## 주요 기능과 Pleos SDK 활용

| 워크플로우 | 기능 | 사용 API |
|---|---|---|
| 주행 전 | 스토리 선택, 이어하기, 처음부터, 새로 시작 | 앱 내 저장소 |
| 주행 전 | 남은 시간으로 페이싱 결정. 10분 미만은 한 사건으로 압축, 10분 이상은 한 장 전체 | NaviHelper `onDestinationInfo.remainTime` |
| 주행 중 | 급가속·급제동 시 캐릭터 반응. 반응은 30초에 한 번이고, 집계는 매번 합니다 | `android.car` `PERF_VEHICLE_SPEED`로 속도 변화율 계산 |
| 주행 중 | 정체 구간 진입 시 다음 장면을 앞당겨 들려줌 | NaviHelper `onRouteStateInfo.trafficStatus` |
| 주행 중 | 턴바이턴 안내를 캐릭터 목소리로 전달 | NaviHelper `onTBTInfo` |
| 주행 중 | 대사와 선택지 음성 출력, 음성으로 답하기 | Pleos TextToSpeech, SpeechToText (ON_DEVICE, ko-KR) |
| 주행 중 | 우회를 고르면 경로 재탐색. 운전자가 직접 고른 경우에만 하고, 경로 안내 중일 때만 합니다 | NaviHelper `changeRouteOption`, `requestReRoute` |
| 주행 중 | 주행 제한 상태 표시와 큰 버튼 위주 UI | `CarUxRestrictionsManager` |
| 비상 | 에어백 전개 시 모든 음성을 끊고 SOS 화면으로 전환. 진행 상황은 저장 | Vehicle Safety `registerSeatAirbagDeployed` |
| 도착 | 1분 전 소결말과 스마트 세이브. P단 체결 시 제한 해제와 주행 리포트(안전 점수, 진행률, 보상 카드) | Vehicle Gear `registerGearSelection` |

## 구조

- `android/app/src/main/java/com/drama/app/core`는 플랫폼과 무관한 엔진입니다. 주행 전 흐름, 페이싱, 주행 루프, 리포트를 담당하며, 이벤트를 받아 상태와 효과 목록을 돌려주는 순수 함수로만 되어 있습니다.
- `android/.../platform`은 Pleos SDK와 `android.car` 신호를 엔진 이벤트로 바꾸고, 엔진 효과를 음성과 경로 요청으로 내보냅니다.
- `android/.../authoring`은 시나리오 작성 검증, 기본 테마 3종, 계정별 저장소입니다. 기본 테마는 `src/authoring/builtin.ts`와 같은 내용입니다.
- `src/`는 같은 엔진의 TypeScript 프로토타입과 웹 시뮬레이터입니다(`npm install && npm run dev`).

## 검증

- 단위 테스트는 23개입니다. 엔진, 작성, 저장소, 음성 매칭, 급가감속 판정을 다룹니다. 실행 명령은 `./gradlew :app:testReleaseUnitTest`입니다.
- 에뮬레이터 E2E는 `android/scripts/e2e.sh`입니다. Pleos Connect v2.1.0 에뮬레이터에서 아래 흐름을 실제로 탭하며 검증합니다.
  1. 스토리 선택, 새로 시작, 15분 주행
  2. 선택지, 급제동 반응, 정체
  3. 도착, P단, 리포트와 보상 카드
  4. 이어하기로 2장 시작, 에어백 SOS

## 알려진 한계

- 에뮬레이터는 user 빌드라서 기어와 속도를 adb로 주입할 수 없습니다. 기어는 Pleos Virtual Console로 바꾸거나, 시연 모드의 기어 P 버튼으로 대신합니다.
- NaviHelper의 `remainTime`은 초 단위로 가정했습니다. 경로 안내 중 남은 시간이 맞는지는 실제 경로로 확인해야 합니다.
- BGM은 상태만 표시합니다. 음원은 아직 넣지 않았습니다.
- 이야기는 미리 쓴 장면으로 진행됩니다. LLM으로 장면을 생성하는 기능은 다음 단계입니다.
