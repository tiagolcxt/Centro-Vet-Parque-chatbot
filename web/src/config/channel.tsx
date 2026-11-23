import { AlertTriangle, CheckCircle2 } from "lucide-react";
import { useEffect, useState } from "react";
import { api } from "../api";
import { useApp, useLoad } from "../app-context";
import { useToast } from "../ui";
import { CopyButton } from "./tools";

interface ChannelView {
  configured: boolean;
  phoneNumberId: string | null;
  displayPhone: string | null;
  wabaId: string | null;
  active: boolean;
  tokenSaved: boolean;
  updatedAt: string | null;
  webhookReady: boolean;
  encryptionReady: boolean;
  webhookPath: string;
}

interface TestResult {
  ok: boolean;
  displayPhone: string | null;
  verifiedName: string | null;
  errorCode: string | null;
  error: string | null;
}

/** Canal WhatsApp da clínica (Etapa S4, ADR-0023). Ligar ou trocar o número exige permissão de publicar. */
export function ChannelSettings() {
  const { can } = useApp();
  const toast = useToast();
  const view = useLoad(() => api.get<ChannelView>("/channel"), []);
  const [phoneNumberId, setPhoneNumberId] = useState("");
  const [displayPhone, setDisplayPhone] = useState("");
  const [wabaId, setWabaId] = useState("");
  const [token, setToken] = useState("");
  const [active, setActive] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [test, setTest] = useState<TestResult | null>(null);
  const [busy, setBusy] = useState(false);
  const v = view.data;
  const editable = can("config:publish");

  useEffect(() => {
    if (v) {
      setPhoneNumberId(v.phoneNumberId ?? "");
      setDisplayPhone(v.displayPhone ?? "");
      setWabaId(v.wabaId ?? "");
      setActive(v.configured ? v.active : true);
    }
  }, [v]);

  const save = async () => {
    setBusy(true);
    try {
      await api.put("/channel", { phoneNumberId, displayPhone, wabaId, accessToken: token || null, active });
      setToken("");
      setError(null);
      setTest(null);
      toast(active ? "Canal salvo e ligado." : "Canal salvo e desligado.");
      view.reload();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const runTest = async () => {
    setBusy(true);
    try {
      setTest(await api.post<TestResult>("/channel/test"));
    } catch (e) {
      toast((e as Error).message, "error");
    } finally {
      setBusy(false);
    }
  };

  const webhookUrl = window.location.origin + (v?.webhookPath ?? "/webhooks/whatsapp");

  return (
    <>
      <section className="card config-section">
        <header>
          <h2>WhatsApp</h2>
          <p className="muted small">
            Liga o número da clínica na Cloud API da Meta. As mensagens dos tutores chegam na caixa de entrada e as respostas
            saem por esse número. Enquanto a clínica testa, use só números de teste e dados fictícios.
          </p>
        </header>
        {v && (
          <ul className="warnings">
            {!v.webhookReady && (
              <li className="warning-critical">
                <AlertTriangle size={14} aria-hidden />{" "}
                <span title="Variáveis WHATSAPP_APP_SECRET e WHATSAPP_VERIFY_TOKEN">
                  Falta configurar a ligação com a Meta no servidor: nenhuma mensagem chega até isso ser feito. Peça ao suporte técnico.
                </span>
              </li>
            )}
            {!v.encryptionReady && (
              <li className="warning-critical">
                <AlertTriangle size={14} aria-hidden />{" "}
                <span title="Variável WHATSAPP_TOKEN_KEY">
                  Falta configurar no servidor a chave que protege o token: não dá para salvar o WhatsApp. Peça ao suporte técnico.
                </span>
              </li>
            )}
            {v.configured && (
              <li>
                {v.active ? <CheckCircle2 size={14} aria-hidden /> : <AlertTriangle size={14} aria-hidden />}{" "}
                {v.active ? "Ligado" : "Desligado"}
                {v.displayPhone ? ` · ${v.displayPhone}` : ""}
                {v.updatedAt ? ` · alterado em ${new Date(v.updatedAt).toLocaleString("pt-BR")}` : ""}
              </li>
            )}
          </ul>
        )}

        <div className="form-grid">
          <div className="field">
            <label htmlFor="ch-phone-id">Identificação do número de telefone</label>
            <input id="ch-phone-id" inputMode="numeric" disabled={!editable} value={phoneNumberId} onChange={(e) => setPhoneNumberId(e.target.value.trim())} />
            <p className="muted small">Na Meta: Casos de uso &gt; WhatsApp &gt; Configuração da API. Só números.</p>
          </div>
          <div className="field">
            <label htmlFor="ch-display">Número como aparece para o tutor</label>
            <input id="ch-display" maxLength={30} disabled={!editable} placeholder="+55 00 0000-0000" value={displayPhone} onChange={(e) => setDisplayPhone(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="ch-waba">Identificação da conta do WhatsApp Business (opcional)</label>
            <input id="ch-waba" inputMode="numeric" disabled={!editable} value={wabaId} onChange={(e) => setWabaId(e.target.value.trim())} />
          </div>
          <div className="field">
            <label htmlFor="ch-token">Token de acesso</label>
            <input
              id="ch-token"
              type="password"
              autoComplete="off"
              disabled={!editable}
              placeholder={v?.tokenSaved ? "Salvo. Preencha só para trocar." : "Token permanente do usuário do sistema"}
              value={token}
              onChange={(e) => setToken(e.target.value)}
            />
            <p className="muted small">Fica cifrado no servidor e nunca volta para esta tela.</p>
          </div>
        </div>
        <label className="inline-row">
          <input type="checkbox" disabled={!editable} checked={active} onChange={(e) => setActive(e.target.checked)} /> Canal ligado
        </label>
        {error && <p className="error">{error}</p>}
        {editable ? (
          <div className="inline-row">
            <button className="primary" disabled={busy || !phoneNumberId || (!v?.tokenSaved && !token)} onClick={() => void save()}>
              Salvar
            </button>
            {v?.configured && (
              <button className="small-button" disabled={busy} onClick={() => void runTest()}>
                Testar conexão
              </button>
            )}
          </div>
        ) : (
          <p className="muted small">Só quem tem permissão de publicar liga ou troca o número.</p>
        )}
        {test && (
          <p className={test.ok ? "" : "error"}>
            {test.ok ? (
              <>
                <CheckCircle2 size={14} aria-hidden /> Conexão ok: {test.verifiedName ?? "nome não informado"}
                {test.displayPhone ? ` (${test.displayPhone})` : ""}.
              </>
            ) : (
              <>
                <AlertTriangle size={14} aria-hidden /> A Meta recusou ({test.errorCode}): {test.error}
              </>
            )}
          </p>
        )}
      </section>

      <section className="card config-section">
        <header>
          <h2>Webhook na Meta</h2>
          <p className="muted small">
            Em Casos de uso &gt; WhatsApp &gt; Configuração, cadastre o endereço abaixo e o mesmo token de verificação que está
            no servidor. Depois assine o campo <strong>messages</strong> (e <strong>smb_message_echoes</strong>, se a clínica
            também responde pelo app no celular). O endereço precisa ser público e com HTTPS.
          </p>
        </header>
        <div className="inline-row">
          <code className="grow ellipsis">{webhookUrl}</code>
          <CopyButton text={webhookUrl} />
        </div>
      </section>
    </>
  );
}
