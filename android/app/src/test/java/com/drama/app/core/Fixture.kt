package com.drama.app.core

private val branch = Branch(
    detour = Option("우회로로 빠진다", "우회로", "현명한 판단이야!"),
    keep = Option("기존 경로 유지", "직진", "배짱 하나는 인정하지."),
    timeoutLine = "답이 없군. 원래 길로 가지.",
)

val fixture = Scenario(
    id = "s1",
    source = ScenarioSource.BUILTIN,
    genre = "로맨스 판타지",
    title = "호위기사",
    premise = "운전자는 마부다.",
    persona = Persona(
        "기사", "까칠한 기사", "⚔️",
        Reactions("말을 진정시켜라.", "앞을 잘 살피라고!", "부드럽게 몰아라.", "길이 막혔군."),
        listOf("경계", "신뢰", "동료"),
    ),
    chapters = listOf(
        Chapter(
            "c1", "제1장", "출발하지.", "지난 이야기: 성문을 나섰다.",
            listOf(
                Beat("s1", SceneKind.SUB, "잘 모는군."),
                Beat("s2", SceneKind.CORE, "매복이다! 우회로로 빠질까, 직진할까?", branch),
                Beat("s3", SceneKind.SUB, "친서를 지켜야 한다."),
                Beat("s4", SceneKind.CORE, "은신처가 보인다."),
            ),
            "다음에 계속.", "1장 끝!", "성문 앞의 기사",
        ),
        Chapter("c2", "제2장", "밤이다.", "지난 이야기: 은신처에 왔다.", listOf(Beat("t1", SceneKind.CORE, "추격이다.")), "다음에.", "2장 끝!", "달빛 카드"),
    ),
)
