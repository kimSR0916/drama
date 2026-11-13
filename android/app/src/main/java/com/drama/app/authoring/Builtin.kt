package com.drama.app.authoring

import com.drama.app.core.Scenario
import com.drama.app.core.ScenarioSource

private val drafts: Map<String, ScenarioDraft> = linkedMapOf(
    "builtin-runaway-train" to ScenarioDraft(
        title = "폭주 기관차 777",
        premise = "운전자는 브레이크가 고장 난 급행열차 777호의 기관사다. 종착역까지 남은 시간이 곧 열차를 멈출 방법을 찾을 시간이다. 실제 차를 부드럽게 몰수록 열차도 안정된다.",
        personaName = "미오",
        personaTone = "침착하지만 속으로는 떨고 있는 신입 차장",
        reactions = ReactionsDraft(harshAccel = "미오: 기관사님, 속도 올리면 안 돼요! 진짜 차는 천천히, 열차는 제가 붙잡고 있을게요.", harshBrake = "미오: 으악, 객실 승객들이 앞으로 쏠렸어요! 급정거는 우리 열차엔 금물이에요.", harshTurn = "미오: 커브에서 바퀴가 선로 위로 들렸어요! 핸들은 부드럽게요.", congestion = "미오: 선로에 양 떼가 나타나서 속도가 줄었어요. 이 틈에 브레이크 배선을 다시 볼게요."),
        chapters = listOf(
            ChapterDraft(
                title = "1장 브레이크가 듣지 않는다",
                opening = "미오: 기관사님, 큰일이에요. 777호 브레이크가 먹통이에요. 종착역에 닿기 전까지 멈출 방법을 찾아야 해요.",
                beats = listOf(
                    BeatDraft(
                        "미오: 앞쪽에 공사 구간이 있대요. 우회 선로로 돌아갈까요, 그대로 직진할까요?",
                        listOf(
                            ChoiceDraft("우회 선로", "미오: 선로 전환기를 당겼어요! 경로를 바꿀게요.", detour = true),
                            ChoiceDraft("그대로 직진", "미오: 알겠어요. 공사 인부들에게 무전으로 대피하라고 할게요."),
                        ),
                    ),
                    BeatDraft("미오: 기관실 바닥에서 낡은 수동 브레이크 레버를 찾았어요. 그런데 녹이 슬어서 혼자서는 안 움직여요."),
                    BeatDraft("미오: 3호 객실의 할아버지가 예전에 이 열차 기관사였대요! 레버 당기는 법을 알려주신대요."),
                ),
                cliffhanger = "미오: 레버가 반쯤 움직였어요. 다음 운전에서 마저 당겨봐요, 기관사님.",
                finale = "미오: 종착역 바로 앞에서 열차가 멈췄어요! 승객 모두 무사해요. 기관사님이 끝까지 침착했던 덕분이에요.",
            ),
            ChapterDraft(
                title = "2장 사라진 승객",
                opening = "미오: 기관사님, 777호가 다시 운행을 시작했어요. 그런데 승객 명단에 있는 한 명이 열차 어디에도 없어요.",
                beats = listOf(
                    BeatDraft(
                        "미오: 마지막 목격 장소는 식당칸이래요. 식당칸 직원부터 만날까요, 화물칸부터 뒤져볼까요?",
                        listOf(
                            ChoiceDraft("식당칸 직원", "미오: 직원이 커피를 두 잔 주문받았다고 하네요. 한 잔은 누구 거였을까요?"),
                            ChoiceDraft("화물칸", "미오: 화물칸 문이 안쪽에서 잠겨 있어요. 누군가 숨어 있는 게 분명해요."),
                        ),
                    ),
                    BeatDraft("미오: 객실 천장에서 발소리가 들려요. 누군가 열차 지붕 위를 걷고 있어요."),
                    BeatDraft("미오: 사라진 승객은 탈출한 게 아니었어요. 이 열차의 브레이크를 고장 낸 사람을 쫓고 있었대요."),
                ),
                finale = "미오: 브레이크를 고장 낸 범인을 잡았어요. 777호의 진짜 비밀은 다음 노선에서 밝혀질 거예요.",
            ),
        ),
    ),
    "builtin-isekai-truck" to ScenarioDraft(
        title = "이세계 트럭 기사",
        premise = "평범하게 운전하던 운전자는 눈을 떠보니 이세계에 소환되어 있었다. 차는 마력으로 달리는 전설의 용사 전차가 되었고, 요정 내비 루미와 함께 드래곤의 알을 왕성까지 배달해야 한다.",
        personaName = "루미",
        personaTone = "수다스럽고 겁 많은 요정 내비게이터",
        reactions = ReactionsDraft(harshAccel = "루미: 용사님, 마력 엔진이 과열돼요! 살살 밟아주세요.", harshBrake = "루미: 꺄악, 짐칸의 드래곤 알이 흔들렸어요! 금이라도 가면 큰일이에요.", harshTurn = "루미: 으아, 커브에서 제 날개가 꼬였어요. 부드럽게요, 부드럽게!", congestion = "루미: 고블린 행렬이 길을 막았어요. 기다리는 동안 이 세계 이야기를 들려드릴게요."),
        chapters = listOf(
            ChapterDraft(
                title = "1장 소환된 운전자",
                opening = "루미: 일어나셨어요, 용사님? 여긴 아스테리아 왕국이에요. 용사님의 차가 전설의 전차로 선택받았어요!",
                beats = listOf(
                    BeatDraft(
                        "루미: 왕성으로 가는 길은 두 갈래예요. 빠르지만 마물이 나오는 숲길, 느리지만 안전한 왕도. 어디로 갈까요?",
                        listOf(
                            ChoiceDraft("숲길", "루미: 용감하시네요! 숲길로 경로를 바꿀게요.", detour = true),
                            ChoiceDraft("왕도", "루미: 현명한 선택이에요. 왕도로 쭉 갈게요."),
                        ),
                    ),
                    BeatDraft("루미: 짐칸에서 톡톡 소리가 나요. 드래곤의 알이 곧 깨어나려나 봐요!"),
                    BeatDraft("루미: 저 멀리 왕성의 첨탑이 보여요. 공주님이 성문 앞에서 기다리고 있대요."),
                ),
                cliffhanger = "루미: 알에 금이 가기 시작했어요. 다음 운전에서 꼭 왕성까지 데려가 주세요!",
                finale = "루미: 드래곤의 알을 무사히 왕성에 배달했어요! 용사님은 이제 왕국 공식 운송 기사예요.",
            ),
            ChapterDraft(
                title = "2장 아기 드래곤",
                opening = "루미: 용사님, 알에서 아기 드래곤이 태어났어요! 그런데 왜인지 용사님만 엄마로 생각하나 봐요.",
                beats = listOf(
                    BeatDraft(
                        "루미: 아기 드래곤이 배고프대요. 마을 시장에 들를까요, 그냥 갈까요?",
                        listOf(
                            ChoiceDraft("시장 들르기", "루미: 시장 쪽으로 경로를 바꿀게요. 드래곤은 사과를 좋아한대요!", detour = true),
                            ChoiceDraft("그냥 가기", "루미: 그럼 제 비상식량 꿀과자를 나눠줄게요."),
                        ),
                    ),
                    BeatDraft("루미: 아기 드래곤이 창밖으로 작은 불꽃을 뿜었어요. 운전에 방해되지 않게 제가 잘 달래볼게요."),
                    BeatDraft("루미: 마왕군 정찰병이 아기 드래곤을 노리고 있대요. 용사님의 안전 운전이 최고의 방어예요."),
                ),
                finale = "루미: 아기 드래곤이 용사님 무릎에서 잠들었어요. 오늘 운행도 무사히 끝났어요.",
            ),
        ),
    ),
    "builtin-night-taxi" to ScenarioDraft(
        title = "심야 택시: 수상한 손님",
        premise = "운전자는 심야 택시 기사다. 검은 코트를 입은 손님이 목적지를 말하지 않고 '그냥 계속 달려 달라'고 한다. 손님의 이야기를 들을수록 정체가 드러난다.",
        personaName = "손님",
        personaTone = "낮고 느긋한 목소리의 수상한 승객",
        reactions = ReactionsDraft(harshAccel = "손님: 서두를 필요 없어요. 쫓아오는 사람은 없으니까. 아직은요.", harshBrake = "손님: 조심하세요. 이 가방 안의 물건이 깨지면 우리 둘 다 곤란해져요.", harshTurn = "손님: 운전 솜씨가 좋네요. 그래도 커브는 천천히 부탁해요.", congestion = "손님: 막히는군요. 마침 잘됐어요. 제 이야기를 조금 더 들려드리죠."),
        chapters = listOf(
            ChapterDraft(
                title = "1화 검은 코트",
                opening = "손님: 기사님, 목적지는 나중에 말씀드릴게요. 일단 출발해 주세요. 오늘 밤은 길 거예요.",
                beats = listOf(
                    BeatDraft(
                        "손님: 기사님은 비밀을 지킬 줄 아는 사람인가요? 대답에 따라 제 이야기가 달라질 거예요.",
                        listOf(
                            ChoiceDraft("지킨다", "손님: 좋아요. 그럼 이 가방 이야기부터 하죠."),
                            ChoiceDraft("모르겠다", "손님: 솔직하네요. 그런 사람이 오히려 믿을 만하죠."),
                        ),
                    ),
                    BeatDraft("손님: 이 가방엔 30년 전 이 도시에서 사라진 시계탑의 열쇠가 들어 있어요."),
                    BeatDraft("손님: 그리고 그 열쇠를 찾는 사람들이 오늘 밤 저를 따라오고 있어요. 백미러를 보지는 마세요."),
                ),
                cliffhanger = "손님: 오늘은 여기까지 하죠. 다음에 기사님 차를 다시 타게 될 거예요. 우연처럼요.",
                finale = "손님: 고마워요, 기사님. 열쇠는 제자리를 찾았어요. 요금은 시계탑이 다시 울릴 때 받으러 오세요.",
            ),
        ),
    ),
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
