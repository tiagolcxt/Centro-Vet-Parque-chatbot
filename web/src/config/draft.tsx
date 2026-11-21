import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { api, ApiError } from "../api";
import { clone, type Doc } from "./schema";

export interface VersionSummary {
  id: string;
  number: number;
  status: "DRAFT" | "PUBLISHED" | "ARCHIVED";
  note: string | null;
  createdAt: string;
  updatedAt: string;
  publishedAt: string | null;
  revision: number;
}

export interface Version {
  summary: VersionSummary;
  document: Doc;
}

export interface Change {
  key: string;
  before: string | null;
  after: string | null;
}

export interface SectionValidity {
  section: string;
  validUntil: string | null;
  expired: boolean;
  expiringSoon: boolean;
}

export interface Overview {
  published: VersionSummary | null;
  draft: VersionSummary | null;
  validity: SectionValidity[];
  draftDiffers: boolean;
}

interface DraftState {
  doc: Doc | null;
  summary: VersionSummary | null;
  /** Há alterações na tela ainda não salvas no rascunho. */
  dirty: boolean;
  problems: string[];
  saving: boolean;
  error: string | null;
  edit: (change: (doc: Doc) => void) => void;
  save: () => Promise<boolean>;
  /** Troca pelo que o servidor devolveu (modelo aplicado, importação, restauração). */
  replace: (version: Version) => void;
  reload: () => Promise<void>;
}

const DraftContext = createContext<DraftState | null>(null);

export function useDraft(): DraftState {
  const state = useContext(DraftContext);
  if (!state) {
    throw new Error("useDraft fora do DraftProvider");
  }
  return state;
}

/** O rascunho é um só para a clínica: cada tela edita uma cópia local e "Salvar" manda o documento inteiro. */
export function DraftProvider({ children }: { children: ReactNode }) {
  const [doc, setDoc] = useState<Doc | null>(null);
  const [summary, setSummary] = useState<VersionSummary | null>(null);
  const [dirty, setDirty] = useState(false);
  const [problems, setProblems] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const replace = useCallback((version: Version) => {
    setDoc(version.document);
    setSummary(version.summary);
    setDirty(false);
    setProblems([]);
  }, []);

  const reload = useCallback(async () => {
    try {
      replace(await api.get<Version>("/config/draft"));
      setError(null);
    } catch (e) {
      setError((e as Error).message);
    }
  }, [replace]);

  useEffect(() => {
    void reload();
  }, [reload]);

  // Não perder o que foi digitado ao fechar a aba.
  useEffect(() => {
    if (!dirty) {
      return;
    }
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);

  const edit = useCallback((change: (doc: Doc) => void) => {
    setDoc((current) => {
      if (!current) {
        return current;
      }
      const next = clone(current);
      change(next);
      return next;
    });
    setDirty(true);
  }, []);

  const save = useCallback(async () => {
    if (!doc || !summary) {
      return false;
    }
    setSaving(true);
    try {
      replace(await api.put<Version>("/config/draft", { document: doc, revision: summary.revision }));
      return true;
    } catch (e) {
      if (e instanceof ApiError && e.problems.length > 0) {
        setProblems(e.problems);
      }
      setError((e as Error).message);
      return false;
    } finally {
      setSaving(false);
    }
  }, [doc, summary, replace]);

  return (
    <DraftContext.Provider value={{ doc, summary, dirty, problems, saving, error, edit, save, replace, reload }}>
      {children}
    </DraftContext.Provider>
  );
}
