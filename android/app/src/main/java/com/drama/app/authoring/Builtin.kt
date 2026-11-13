package com.drama.app.authoring

import com.drama.app.core.Scenario
import com.drama.app.core.ScenarioSource

private val drafts: Map<String, ScenarioDraft> = linkedMapOf(
    "builtin-detective" to ScenarioDraft(
        title = "야간 탐정 사무소",
        premise = "운전자는 밤의 도시를 누비는 탐정이다. 조수 하루가 무전으로 사건을 함께 푼다.",
        personaName = "하루",
        personaTone = "침착하고 예리한 조수",
        chapters = listOf(
            ChapterDraft(
                title = "1장 사라진 편지",
                opening = "하루: 탐정님, 첫 의뢰예요. 오래된 우체국에서 편지 한 통이 사라졌대요.",
                beats = listOf(
                    BeatDraft(
                        "하루: 마지막으로 편지를 본 사람은 야간 경비원이에요. 먼저 누구를 만나볼까요?",
                        listOf(
                            ChoiceDraft("경비원", "하루: 좋아요. 경비원의 알리바이부터 확인할게요."),
                            ChoiceDraft("우체국장", "하루: 국장님 댁은 다른 방향이에요. 경로를 바꿔둘게요.", detour = true),
                        ),
                    ),
                    BeatDraft("하루: 방금 제보가 들어왔어요. 편지 봉투에 라벤더 향이 났대요."),
                    BeatDraft("하루: 라벤더 향수를 쓰는 사람은 단 한 명. 범인이 보이기 시작하네요."),
                ),
                finale = "하루: 편지는 국장님 서랍 속에 있었어요. 첫 사건 해결이에요, 탐정님.",
            ),
            ChapterDraft(
                title = "2장 가면 무도회",
                opening = "하루: 이번엔 무도회장에서 보석이 사라졌어요. 손님 전원이 가면을 썼대요.",
                beats = listOf(
                    BeatDraft(
                        "하루: 가면 하나가 계단 밑에서 발견됐어요. 주인을 찾아볼까요, 현장을 더 볼까요?",
                        listOf(
                            ChoiceDraft("주인 찾기", "하루: 가면 안쪽에 이니셜이 있어요. 추적해 볼게요."),
                            ChoiceDraft("현장 조사", "하루: 바닥에 보석 가루가 떨어져 있네요."),
                        ),
                    ),
                    BeatDraft("하루: 시계탑이 자정을 알리는 순간 정전이 있었대요. 우연일까요?"),
                    BeatDraft("하루: 정전을 일으킨 사람이 범인이에요. 배전반 열쇠는 단 두 사람만 가지고 있어요."),
                ),
            ),
        ),
    ),
    "builtin-starship" to ScenarioDraft(
        title = "우주 화물선 오리온",
        premise = "운전자는 화물선 오리온의 선장이다. AI 항해사 노바와 함께 은하 배송을 완수한다.",
        personaName = "노바",
        personaTone = "다정하지만 원칙적인 AI 항해사",
        reactions = ReactionsDraft(congestion = "노바: 소행성대에 진입했어요. 속도를 줄이는 동안 보고를 올릴게요."),
        chapters = listOf(
            ChapterDraft(
                title = "1장 첫 출항",
                opening = "노바: 선장님, 오리온 출항 준비 완료. 목적지까지 화물 하나를 안전하게 옮기면 돼요.",
                beats = listOf(
                    BeatDraft(
                        "노바: 화물칸에서 이상한 신호가 잡혀요. 열어볼까요?",
                        listOf(
                            ChoiceDraft("열어본다", "노바: 상자 안에서 작은 로봇이 깨어났어요!"),
                            ChoiceDraft("그대로 둔다", "노바: 규정대로 봉인을 유지할게요."),
                        ),
                    ),
                    BeatDraft("노바: 해적선이 레이더에 잡혔어요. 하지만 우린 화물선이죠. 조용히 지나가요."),
                    BeatDraft("노바: 해적선이 멀어졌어요. 목적지 행성이 보이기 시작해요."),
                ),
            ),
        ),
    ),
    "builtin-radio" to ScenarioDraft(
        title = "새벽 라디오 DJ",
        premise = "운전자는 새벽 라디오의 게스트다. DJ 소리가 청취자 사연을 함께 읽는다.",
        personaName = "소리",
        personaTone = "밝고 수다스러운 DJ",
        chapters = listOf(
            ChapterDraft(
                title = "1화 첫 사연",
                opening = "소리: 새벽 라디오에 오신 걸 환영해요! 오늘의 게스트는 바로 운전 중인 당신이에요.",
                beats = listOf(
                    BeatDraft(
                        "소리: 첫 사연이에요. 짝사랑에게 고백할지 고민이래요. 어떻게 답해줄까요?",
                        listOf(
                            ChoiceDraft("고백해", "소리: 역시! 용기 있는 답변 감사해요."),
                            ChoiceDraft("기다려", "소리: 신중한 조언이네요. 타이밍도 중요하죠."),
                        ),
                    ),
                    BeatDraft("소리: 신청곡이 들어왔어요. 창문 밖 풍경이랑 잘 어울리는 노래래요."),
                    BeatDraft("소리: 사연자에게서 답장이 왔어요! 내일 결과를 알려준대요."),
                ),
            ),
        ),
    ),
)

val BUILTIN_SCENARIOS: List<Scenario> = drafts.map { (id, draft) ->
    when (val result = parseScenarioDraft(draft, id, ScenarioSource.BUILTIN)) {
        is DraftResult.Ok -> result.scenario
        is DraftResult.Invalid -> error("invalid builtin $id: ${result.issues}")
    }
}
