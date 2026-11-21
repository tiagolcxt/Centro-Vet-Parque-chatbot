import { useEffect, useId, useState } from "react";
import { DEPARTMENT_KINDS, display, parse, type Field } from "./schema";

/**
 * Um campo da configuração. Guarda o texto digitado e só devolve o valor quando dá para entender; texto que não dá
 * (preço "cem") fica marcado na hora, sem esperar o servidor.
 */
export function ConfigField({
  field,
  value,
  onChange,
  problems = [],
}: {
  field: Field;
  value: unknown;
  onChange: (value: unknown) => void;
  problems?: string[];
}) {
  const id = useId();
  const [text, setText] = useState(() => display(field.type, value));
  const [invalid, setInvalid] = useState(false);

  // Valor trocado de fora (modelo aplicado, restauração): mostra o novo.
  useEffect(() => {
    setText((current) => {
      const parsed = parse(field.type, current);
      return JSON.stringify(parsed) === JSON.stringify(value ?? null) ? current : display(field.type, value);
    });
  }, [value, field.type]);

  const commit = (next: string) => {
    setText(next);
    const parsed = parse(field.type, next);
    setInvalid(parsed === undefined);
    if (parsed !== undefined) {
      onChange(parsed);
    }
  };

  const messages = problems.map((p) => p.slice(p.indexOf(":") + 1).trim());
  const describedBy = field.help || messages.length || invalid ? id + "-help" : undefined;
  const common = {
    id,
    "aria-invalid": invalid || messages.length > 0 ? true : undefined,
    "aria-describedby": describedBy,
  };

  let input;
  if (field.type === "bool") {
    input = (
      <select {...common} value={text} onChange={(e) => commit(e.target.value)}>
        <option value="">Não informado</option>
        <option value="true">Sim</option>
        <option value="false">Não</option>
      </select>
    );
  } else if (field.key === "department") {
    input = (
      <select {...common} value={text} onChange={(e) => commit(e.target.value)}>
        {DEPARTMENT_KINDS.map(([kind, label]) => (
          <option key={kind} value={kind}>
            {label}
          </option>
        ))}
      </select>
    );
  } else if (field.type === "textarea" || field.type === "days" || field.type === "prices") {
    input = <textarea {...common} rows={field.type === "textarea" ? 2 : 3} value={text} onChange={(e) => commit(e.target.value)} />;
  } else {
    input = (
      <input
        {...common}
        type={field.type === "email" ? "email" : "text"}
        inputMode={field.type === "money" ? "decimal" : field.type === "phone" ? "tel" : undefined}
        value={text}
        onChange={(e) => commit(e.target.value)}
      />
    );
  }

  return (
    <div className={"field" + (invalid || messages.length ? " field-error" : "")}>
      <label htmlFor={id}>
        {field.label}
        {(value === null || value === undefined) && field.type !== "bool" && <span className="unknown"> · não informado</span>}
      </label>
      {input}
      {describedBy && (
        <p id={id + "-help"} className={invalid || messages.length ? "error small" : "muted small"}>
          {invalid ? "Não deu para entender este valor." : messages.length ? messages.join(" ") : field.help}
        </p>
      )}
    </div>
  );
}
