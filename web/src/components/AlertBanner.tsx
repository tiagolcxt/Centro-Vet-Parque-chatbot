import { useEffect, useRef, useState } from "react";
import { AlertTriangle, BellOff, BellRing, Check, ChevronDown, ChevronUp, ClipboardList, MessageSquare } from "lucide-react";
import { api } from "../api";
import { go, useApp, useLoad } from "../app-context";
import { minutesLabel, timeLabel } from "../labels";
import type { AlertItem } from "../types";
import { useToast } from "../ui";

const KIND_LABEL: Record<AlertItem["kind"], string> = {
  CONVERSATION_WAITING: "Tutor esperando resposta",
  REQUEST_UNASSIGNED: "Solicitação sem responsável",
};

function readMuted(): boolean {
  try {
    return localStorage.getItem("alerts-muted") === "1";
  } catch {
    return false;
  }
}

/** Bipe curto, sem arquivo de áudio. O navegador pode bloquear até a pessoa interagir com a página. */
function beep() {
  try {
    const Ctx = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!Ctx) return;
    const ctx = new Ctx();
    [0, 0.25].forEach((offset) => {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.frequency.value = 880;
      gain.gain.setValueAtTime(0.15, ctx.currentTime + offset);
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + offset + 0.18);
      osc.connect(gain).connect(ctx.destination);
      osc.start(ctx.currentTime + offset);
      osc.stop(ctx.currentTime + offset + 0.2);
    });
    window.setTimeout(() => void ctx.close(), 800);
  } catch {
    // sem som: o aviso visual continua
  }
}

/**
 * Faixa de alerta em qualquer tela (E3, achado A10): conversa em que o tutor espera além do prazo e solicitação sem
 * responsável há muito tempo. "Recebi" diz que alguém viu; quem resolve é assumir a conversa ou pegar a solicitação.
 * Toca um bipe quando aparece alerta novo ou escalado sem "Recebi" (dá para silenciar neste navegador).
 */
export function AlertBanner() {
  const { liveTick } = useApp();
  const toast = useToast();
  const [minute, setMinute] = useState(0);
  useEffect(() => {
    const timer = window.setInterval(() => setMinute((m) => m + 1), 60_000);
    return () => window.clearInterval(timer);
  }, []);
  const alerts = useLoad(() => api.get<AlertItem[]>("/alerts").catch(() => [] as AlertItem[]), [liveTick, minute]);
  const [open, setOpen] = useState(false);
  const [muted, setMuted] = useState(readMuted);
  const heard = useRef(new Set<string>());
  const items = alerts.data ?? [];
  const waitingAck = items.filter((a) => !a.acknowledgedAt);

  useEffect(() => {
    const fresh = waitingAck.filter((a) => !heard.current.has(a.id + (a.escalated ? ":escalado" : "")));
    fresh.forEach((a) => heard.current.add(a.id + (a.escalated ? ":escalado" : "")));
    if (fresh.length > 0 && !muted) beep();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [alerts.data]);

  if (items.length === 0) return null;

  const toggleMuted = () => {
    const next = !muted;
    setMuted(next);
    try {
      localStorage.setItem("alerts-muted", next ? "1" : "0");
    } catch {
      // modo privado: vale só nesta aba
    }
  };
  const acknowledge = async (id: string) => {
    try {
      await api.post(`/alerts/${id}/acknowledge`, {});
      toast("Registrado: você viu o alerta");
      alerts.reload();
    } catch (e) {
      toast((e as Error).message, "error");
    }
  };
  const openSubject = (a: AlertItem) =>
    go((a.kind === "CONVERSATION_WAITING" ? "atendimento/" : "solicitacoes/") + a.subjectId);

  const escalated = waitingAck.some((a) => a.escalated);
  const tone = waitingAck.length === 0 ? "calm" : escalated ? "critical" : "warning";
  const summary =
    items.length === 1 ? "1 pendência esperando além do prazo" : `${items.length} pendências esperando além do prazo`;

  return (
    <section className={"alert-banner alert-" + tone} aria-live="polite" aria-label="Alertas de pendências esquecidas">
      <div className="alert-bar">
        <AlertTriangle size={16} aria-hidden />
        <strong>{summary}</strong>
        {waitingAck.length > 0 && (
          <span className="small">
            · {waitingAck.length} sem "Recebi"{escalated ? " · escalado para toda a equipe" : ""}
          </span>
        )}
        <span className="grow" />
        <button className="small-button" onClick={toggleMuted} title={muted ? "Ligar o som dos alertas" : "Silenciar os alertas neste navegador"}>
          {muted ? <BellOff size={14} aria-hidden /> : <BellRing size={14} aria-hidden />}
          {muted ? "Som desligado" : "Som ligado"}
        </button>
        <button className="small-button" onClick={() => setOpen(!open)} aria-expanded={open}>
          {open ? <ChevronUp size={14} aria-hidden /> : <ChevronDown size={14} aria-hidden />}
          {open ? "Esconder" : "Ver"}
        </button>
      </div>
      {open && (
        <ul className="alert-list">
          {items.map((a) => (
            <li key={a.id} className={a.escalated && !a.acknowledgedAt ? "alert-row escalated" : "alert-row"}>
              {a.kind === "CONVERSATION_WAITING" ? <MessageSquare size={16} aria-hidden /> : <ClipboardList size={16} aria-hidden />}
              <div className="grow">
                <strong>{a.title}</strong>
                {a.what && <span className="muted small"> · {a.what}</span>}
                <span className="block muted small">
                  {KIND_LABEL[a.kind]} há {minutesLabel(a.waitingMinutes)}
                  {a.escalated && !a.acknowledgedAt ? " · escalado" : ""}
                  {a.acknowledgedAt ? ` · ${a.acknowledgedByName ?? "alguém"} viu às ${timeLabel(a.acknowledgedAt)}` : ""}
                </span>
              </div>
              {!a.acknowledgedAt && (
                <button className="small-button" onClick={() => void acknowledge(a.id)} title="Avisa a equipe que você viu. Não resolve a pendência.">
                  <Check size={14} aria-hidden /> Recebi
                </button>
              )}
              <button className="small-button primary" onClick={() => openSubject(a)}>
                Abrir
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
