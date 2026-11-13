package com.drama.app.core

val fixture = Scenario(
    id = "s1",
    source = ScenarioSource.BUILTIN,
    title = "야간 탐정",
    premise = "운전자는 도시를 누비는 탐정이다.",
    persona = Persona(
        "하루", "침착한 조수",
        Reactions("너무 서두르면 단서를 놓쳐요.", "괜찮아요? 천천히 가요.", "커브는 부드럽게요.", "막히는 동안 단서를 정리해 볼까요?"),
    ),
    chapters = listOf(
        Chapter(
            "c1", "1장 사라진 편지", "첫 번째 의뢰가 들어왔어요.",
            listOf(
                Beat("b1", "편지가 사라졌대요.", listOf(
                    Choice("fast", "지름길로", "지름길로 가죠.", RouteHint.DETOUR),
                    Choice("safe", "원래 길로", "원래 길로 가죠.", RouteHint.KEEP),
                )),
                Beat("b2", "용의자는 우체부예요."),
                Beat("b3", "우체부를 만났어요."),
            ),
            "다음에 계속.", "1장 해결!",
        ),
        Chapter("c2", "2장 가면", "새 의뢰예요.", listOf(Beat("b4", "가면을 쓴 남자.")), "다음에 계속.", "2장 해결!"),
    ),
)
