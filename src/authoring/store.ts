import type { SaveData, Scenario } from "../core/scenario";

export interface KeyValue {
  get(key: string): string | null;
  set(key: string, value: string): void;
  remove(key: string): void;
  keys(): string[];
}

export interface AccountStore {
  listScenarios(): Scenario[];
  getScenario(id: string): Scenario | undefined;
  putScenario(scenario: Scenario): void;
  deleteScenario(id: string): void;
  getSave(scenarioId: string): SaveData | undefined;
  putSave(save: SaveData): void;
}

export function memoryKeyValue(): KeyValue {
  const map = new Map<string, string>();
  return {
    get: (k) => map.get(k) ?? null,
    set: (k, v) => void map.set(k, v),
    remove: (k) => void map.delete(k),
    keys: () => [...map.keys()],
  };
}

function readJson<T>(kv: KeyValue, key: string): T | undefined {
  const raw = kv.get(key);
  if (raw === null) return undefined;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return undefined;
  }
}

export function accountStore(kv: KeyValue, accountId: string): AccountStore {
  const scenarioKey = (id: string) => `${accountId}/scenario/${id}`;
  const saveKey = (id: string) => `${accountId}/save/${id}`;
  const scenarioPrefix = `${accountId}/scenario/`;
  return {
    listScenarios: () =>
      kv
        .keys()
        .filter((k) => k.startsWith(scenarioPrefix))
        .sort()
        .flatMap((k) => readJson<Scenario>(kv, k) ?? []),
    getScenario: (id) => readJson<Scenario>(kv, scenarioKey(id)),
    putScenario: (s) => kv.set(scenarioKey(s.id), JSON.stringify(s)),
    deleteScenario: (id) => {
      kv.remove(scenarioKey(id));
      kv.remove(saveKey(id));
    },
    getSave: (id) => readJson<SaveData>(kv, saveKey(id)),
    putSave: (save) => kv.set(saveKey(save.scenarioId), JSON.stringify(save)),
  };
}
