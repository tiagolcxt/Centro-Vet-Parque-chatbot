import { useState, type FormEvent } from "react";
import { api } from "../api";
import { go, queryParam } from "../app-context";

/** Link de convite: #/convite?token=... (RF-S02). Define a senha e manda para o login. */
export function AcceptInvite() {
  const token = queryParam("token") ?? "";
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (password !== confirm) {
      setError("As senhas não conferem.");
      return;
    }
    try {
      await api.post("/auth/accept-invite", { token, password });
      setDone(true);
    } catch (err) {
      setError((err as Error).message);
    }
  };

  return (
    <div className="auth">
      <form className="card auth-card" onSubmit={submit}>
        <h1>Definir senha</h1>
        {done ? (
          <>
            <p>Senha definida. Agora é só entrar.</p>
            <button type="button" className="primary" onClick={() => go("")}>
              Ir para o login
            </button>
          </>
        ) : (
          <>
            <p className="muted">Use de 10 a 72 caracteres.</p>
            <label>
              Nova senha
              <input type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={10} maxLength={72} />
            </label>
            <label>
              Repita a senha
              <input type="password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required />
            </label>
            {!token && <p className="error">Link sem token. Peça um novo ao administrador.</p>}
            {error && (
              <p className="error" role="alert">
                {error}
              </p>
            )}
            <button className="primary" disabled={!token}>
              Salvar senha
            </button>
          </>
        )}
      </form>
    </div>
  );
}
