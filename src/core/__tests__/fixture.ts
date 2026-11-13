import type { Scenario } from "../scenario";

export const scenario: Scenario = {
  id: "s1",
  source: "builtin",
  title: "야간 탐정",
  premise: "운전자는 도시를 누비는 탐정이다.",
  persona: {
    name: "하루",
    tone: "침착한 조수",
    reactions: {
      harshAccel: "너무 서두르면 단서를 놓쳐요.",
      harshBrake: "괜찮아요? 천천히 가요.",
      harshTurn: "커브는 부드럽게요.",
      congestion: "막히는 동안 단서를 정리해 볼까요?",
    },
  },
  chapters: [
    {
      id: "c1",
      title: "1장 사라진 편지",
      opening: "첫 번째 의뢰가 들어왔어요.",
      beats: [
        { id: "b1", line: "편지가 사라졌대요.", choices: [
          { id: "fast", label: "지름길로", reply: "지름길로 가죠.", routeHint: "detour" },
          { id: "safe", label: "원래 길로", reply: "원래 길로 가죠.", routeHint: "keep" },
        ] },
        { id: "b2", line: "용의자는 우체부예요." },
        { id: "b3", line: "우체부를 만났어요." },
      ],
      cliffhanger: "다음에 계속.",
      finale: "1장 해결!",
    },
    {
      id: "c2",
      title: "2장 가면",
      opening: "새 의뢰예요.",
      beats: [{ id: "b4", line: "가면을 쓴 남자." }],
      cliffhanger: "다음에 계속.",
      finale: "2장 해결!",
    },
  ],
};
