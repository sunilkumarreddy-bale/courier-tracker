import React, { useEffect, useRef, useState } from "react";
import {
  Package,
  LayoutDashboard,
  Truck,
  Search,
  Bell,
  Plus,
  ArrowUpRight,
  ArrowRight,
  MapPin,
  Check,
  Clock,
  AlertCircle,
  X,
  LogOut,
  Menu,
  ChevronLeft,
  ChevronRight,
  ShieldCheck,
  Users,
  Copy,
  Box,
  ArrowDownLeft,
  CheckCircle2,
  RefreshCw,
} from "lucide-react";
import { request } from "./api";
const statuses = [
  "REGISTERED",
  "PICKED_UP",
  "IN_TRANSIT",
  "OUT_FOR_DELIVERY",
  "DELIVERED",
  "DELIVERY_FAILED",
  "RETURNED",
  "CANCELLED",
];
const label = (s) =>
  s
    ?.toLowerCase()
    .replaceAll("_", " ")
    .replace(/\b\w/g, (c) => c.toUpperCase());
const date = (s) =>
  new Date(s).toLocaleDateString(undefined, {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
const timestamp = (s) =>
  new Date(s).toLocaleString(undefined, {
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
  });
const today = () => new Date().toISOString().slice(0, 10);
const initialParcel = {
  senderName: "",
  senderEmail: "",
  senderPhone: "",
  senderAddress: "",
  senderCity: "",
  recipientName: "",
  recipientEmail: "",
  recipientPhone: "",
  recipientAddress: "",
  recipientCity: "",
  description: "",
  weightKg: "",
  serviceType: "STANDARD",
  expectedDelivery: today(),
};
function Badge({ status }) {
  return (
    <span className={`badge ${status?.toLowerCase()}`}>
      <i />
      {label(status)}
    </span>
  );
}
function Field({
  name,
  label: caption,
  type = "text",
  required = true,
  errors = {},
  ...props
}) {
  return (
    <label className="field">
      <span>{caption}</span>
      <input
        name={name}
        type={type}
        required={required}
        aria-invalid={!!errors[name]}
        {...props}
      />
      {errors[name] && <small className="field-error">{errors[name]}</small>}
    </label>
  );
}
function Empty({
  title = "Nothing here yet",
  text = "New updates will appear here.",
}) {
  return (
    <div className="empty">
      <Box size={32} />
      <h3>{title}</h3>
      <p>{text}</p>
    </div>
  );
}
function ErrorNote({ message }) {
  return (
    message && (
      <div className="error" role="alert">
        <AlertCircle size={17} />
        {message}
      </div>
    )
  );
}
function Modal({ title, subtitle, children, onClose }) {
  const ref = useRef(),
    closeRef = useRef(onClose);
  closeRef.current = onClose;
  useEffect(() => {
    const prior = document.activeElement;
    document.body.style.overflow = "hidden";
    ref.current?.focus();
    const handle = (e) => {
      if (e.key === "Escape") closeRef.current();
      if (e.key === "Tab") {
        const nodes = ref.current?.querySelectorAll(
          'button:not([disabled]),input,select,textarea,[tabindex="0"]',
        );
        if (!nodes?.length) return;
        const first = nodes[0],
          last = nodes[nodes.length - 1];
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    };
    document.addEventListener("keydown", handle);
    return () => {
      document.body.style.overflow = "";
      document.removeEventListener("keydown", handle);
      prior?.focus();
    };
  }, []);
  return (
    <div
      className="overlay"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <section
        className="modal"
        ref={ref}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-title"
      >
        <header>
          <div>
            <p className="eyebrow">COURIER WORKSPACE</p>
            <h2 id="modal-title">{title}</h2>
            {subtitle && <p>{subtitle}</p>}
          </div>
          <button
            className="icon-button"
            aria-label="Close dialog"
            onClick={onClose}
          >
            <X />
          </button>
        </header>
        {children}
      </section>
    </div>
  );
}
function Timeline({ events, publicView = false }) {
  return (
    <ol className="timeline">
      {[...events].reverse().map((e, i) => (
        <li key={e.id || `${e.occurredAt}-${i}`}>
          <span className={`timeline-marker ${i === 0 ? "latest" : ""}`}>
            {i === 0 ? <Package size={14} /> : <Check size={12} />}
          </span>
          <div>
            <div className="timeline-top">
              <strong>{label(e.status)}</strong>
              <time>{timestamp(e.occurredAt)}</time>
            </div>
            <p>
              <MapPin size={13} />
              {e.location}
            </p>
            {!publicView && (
              <>
                <p className="timeline-note">{e.note}</p>
                <small>Updated by {e.actorName}</small>
              </>
            )}
          </div>
        </li>
      ))}
    </ol>
  );
}
function Tracking({ onBack, compact = false }) {
  const [id, setId] = useState(
    new URLSearchParams(window.location.search).get("id") || "",
  );
  const [result, setResult] = useState(null),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  async function track(e) {
    e?.preventDefault();
    if (!id.trim()) return;
    setBusy(true);
    setError("");
    setResult(null);
    try {
      setResult(await request(`/tracking/${encodeURIComponent(id.trim())}`));
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  useEffect(() => {
    if (id) track();
  }, []);
  return (
    <div className={`tracking-page ${compact ? "compact" : ""}`}>
      {!compact && (
        <nav className="public-nav">
          <a className="brand" href="#track">
            <span className="brand-mark">
              <Package size={23} />
            </span>
            courier<span className="brand-light">tracker</span>
          </a>
          <button className="button outline" onClick={onBack}>
            Workspace sign in <ArrowUpRight size={16} />
          </button>
        </nav>
      )}
      <div className="tracking-heading">
        <span className="tag">
          <Truck size={14} /> FOLLOW YOUR DELIVERY
        </span>
        <h1>
          A little clarity.
          <br />
          <span>Every step of the way.</span>
        </h1>
        <p>Enter your tracking ID to see where your parcel is headed.</p>
      </div>
      <form className="track-form" onSubmit={track}>
        <Search size={20} />
        <label className="sr-only" htmlFor="tracking-id">
          Tracking ID
        </label>
        <input
          id="tracking-id"
          value={id}
          onChange={(e) => setId(e.target.value)}
          placeholder="e.g. CT-DEMO-1004"
          required
          maxLength={40}
        />
        <button className="button primary" disabled={busy}>
          {busy ? "Tracking…" : "Track parcel"}
          <ArrowRight size={16} />
        </button>
      </form>
      <ErrorNote message={error} />
      {result ? (
        <section className="tracking-result panel">
          <div className="section-head">
            <div>
              <p className="eyebrow">YOUR SHIPMENT</p>
              <h2>{result.trackingId}</h2>
            </div>
            <Badge status={result.status} />
          </div>
          <div className="route">
            <div>
              <small>FROM</small>
              <h3>{result.origin}</h3>
            </div>
            <span>
              <Truck size={22} />
              <ArrowRight size={18} />
            </span>
            <div>
              <small>TO</small>
              <h3>{result.destination}</h3>
            </div>
          </div>
          <div className="tracking-meta">
            <span>
              <Clock size={16} />
              Expected delivery: {date(result.expectedDelivery)}
            </span>
            <span>{label(result.serviceType)} service</span>
          </div>
          <h3 className="subheading">Shipment journey</h3>
          <Timeline events={result.history} publicView />
        </section>
      ) : (
        !error && (
          <div className="tracking-hint">
            <ShieldCheck size={18} />
            <p>
              Tracking shows shipment progress without exposing personal contact
              details.
            </p>
          </div>
        )
      )}
      {!compact && (
        <footer className="public-footer">
          Courier Tracker · Built for smoother deliveries
        </footer>
      )}
    </div>
  );
}
function Login({ onLogin, onTrack }) {
  const [email, setEmail] = useState(""),
    [password, setPassword] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      onLogin(
        await request("/auth/login", {
          method: "POST",
          body: { email, password },
        }),
      );
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="login-layout">
      <section className="login-art">
        <div className="brand">
          <span className="brand-mark">
            <Package />
          </span>
          courier<span className="brand-light">tracker</span>
        </div>
        <div>
          <p className="eyebrow">DELIVERY, CONNECTED.</p>
          <h1>
            From your doorstep.
            <br />
            To their hands.
          </h1>
          <p>
            One workspace for every parcel, every courier,
            <br />
            and every step in between.
          </p>
          <div className="parcel-illustration" aria-hidden="true">
            <div className="orbit orbit-one" />
            <div className="orbit orbit-two" />
            <div className="big-box">
              <Package size={94} strokeWidth={1} />
            </div>
            <div className="floating-chip chip-one">
              <CheckCircle2 size={18} />
              Delivered with care
            </div>
            <div className="floating-chip chip-two">
              <MapPin size={18} />
              Always in the loop
            </div>
          </div>
        </div>
        <small>LESS GUESSWORK. MORE GOOD DELIVERIES.</small>
      </section>
      <section className="login-form">
        <div>
          <span className="tag">YOUR OPERATIONS WORKSPACE</span>
          <h2>Welcome back.</h2>
          <p>Sign in to keep things moving.</p>
          <form onSubmit={submit}>
            <ErrorNote message={error} />
            <Field
              name="email"
              label="Work email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoComplete="username"
              placeholder="you@company.com"
            />
            <Field
              name="password"
              label="Password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
            />
            <button className="button primary full" disabled={busy}>
              {busy ? "Signing in…" : "Sign in to workspace"}
              <ArrowRight size={17} />
            </button>
          </form>
          <div className="login-divider">Just checking on a delivery?</div>
          <button className="button outline full" onClick={onTrack}>
            <Search size={17} />
            Track a parcel
          </button>
          <details className="demo-help">
            <summary>Running the local demo?</summary>
            <p>
              Start the API with <code>DEMO_ENABLED=true</code>.
            </p>
            <p>
              Admin: admin@courier.local / DemoAdmin!2026
              <br />
              Agent: agent@courier.local / DemoAgent!2026
            </p>
            <button
              className="text-button"
              onClick={() => {
                setEmail("admin@courier.local");
                setPassword("DemoAdmin!2026");
              }}
            >
              Fill demo admin credentials
            </button>
          </details>
        </div>
      </section>
    </div>
  );
}
function ParcelTable({ parcels, onSelect, loading }) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Shipment</th>
            <th>Destination</th>
            <th>Status</th>
            <th>Assigned to</th>
            <th>Expected delivery</th>
            <th>
              <span className="sr-only">Details</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {parcels.map((p) => (
            <tr key={p.id}>
              <td>
                <button
                  className="shipment-link"
                  onClick={() => onSelect(p.id)}
                >
                  {p.trackingId}
                  <ArrowUpRight size={13} />
                </button>
                <small>
                  {p.recipientName} · {p.weightKg} kg
                </small>
              </td>
              <td>
                <strong>{p.recipientCity}</strong>
                <small>From {p.senderCity}</small>
              </td>
              <td>
                <Badge status={p.status} />
              </td>
              <td>
                <span className={p.agent ? "agent-name" : "unassigned"}>
                  {p.agent ? (
                    <>
                      <span className="avatar small">
                        {p.agent.name
                          .split(" ")
                          .map((x) => x[0])
                          .join("")}
                      </span>
                      {p.agent.name}
                    </>
                  ) : (
                    "Unassigned"
                  )}
                </span>
              </td>
              <td>
                {date(p.expectedDelivery)}
                <small>{label(p.serviceType)}</small>
              </td>
              <td>
                <button
                  className="icon-button"
                  aria-label={`View ${p.trackingId}`}
                  onClick={() => onSelect(p.id)}
                >
                  <ChevronRight size={18} />
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {loading ? (
        <div className="loading">Loading shipments…</div>
      ) : (
        !parcels.length && (
          <Empty
            title="No shipments found"
            text="Try another filter, or register your first parcel."
          />
        )
      )}
    </div>
  );
}
function CreateParcel({ onClose, onCreated }) {
  const [form, setForm] = useState(initialParcel),
    [error, setError] = useState(""),
    [errors, setErrors] = useState({}),
    [busy, setBusy] = useState(false);
  function field(name, caption, type = "text", extra = {}) {
    return (
      <Field
        name={name}
        label={caption}
        type={type}
        errors={errors}
        value={form[name]}
        onChange={(e) => setForm({ ...form, [name]: e.target.value })}
        {...extra}
      />
    );
  }
  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError("");
    setErrors({});
    try {
      const p = await request("/parcels", {
        method: "POST",
        body: { ...form, weightKg: Number(form.weightKg) },
      });
      onCreated(p);
    } catch (e) {
      setError(e.message);
      setErrors(e.fields || {});
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal
      title="Register a parcel"
      subtitle="A new journey starts here. All fields are required."
      onClose={onClose}
    >
      <form onSubmit={submit}>
        <ErrorNote message={error} />
        <h3 className="form-section">
          <ArrowUpRight size={18} />
          Sender details
        </h3>
        <div className="form-grid">
          {field("senderName", "Full name", "text", { maxLength: 100 })}
          {field("senderEmail", "Email", "email", { maxLength: 254 })}
          {field("senderPhone", "Phone", "tel", {
            maxLength: 20,
            pattern: "[+0-9 ()-]{7,20}",
          })}
          {field("senderCity", "City", "text", { maxLength: 100 })}
          <div className="span-two">
            {field("senderAddress", "Street address", "text", {
              maxLength: 300,
            })}
          </div>
        </div>
        <h3 className="form-section">
          <ArrowDownLeft size={18} />
          Recipient details
        </h3>
        <div className="form-grid">
          {field("recipientName", "Full name", "text", { maxLength: 100 })}
          {field("recipientEmail", "Email", "email", { maxLength: 254 })}
          {field("recipientPhone", "Phone", "tel", {
            maxLength: 20,
            pattern: "[+0-9 ()-]{7,20}",
          })}
          {field("recipientCity", "City", "text", { maxLength: 100 })}
          <div className="span-two">
            {field("recipientAddress", "Street address", "text", {
              maxLength: 300,
            })}
          </div>
        </div>
        <h3 className="form-section">
          <Package size={18} />
          Parcel details
        </h3>
        <div className="form-grid">
          <div className="span-two">
            {field("description", "Contents description", "text", {
              maxLength: 300,
            })}
          </div>
          {field("weightKg", "Weight (kg)", "number", {
            min: 0.01,
            max: 1000,
            step: 0.01,
          })}
          <label className="field">
            <span>Service</span>
            <select
              value={form.serviceType}
              onChange={(e) =>
                setForm({ ...form, serviceType: e.target.value })
              }
            >
              <option value="STANDARD">Standard</option>
              <option value="EXPRESS">Express</option>
            </select>
          </label>
          {field("expectedDelivery", "Expected delivery", "date", {
            min: today(),
          })}
        </div>
        <footer className="modal-actions">
          <button
            type="button"
            className="button outline"
            disabled={busy}
            onClick={onClose}
          >
            Cancel
          </button>
          <button className="button primary" disabled={busy}>
            {busy ? "Registering…" : "Register parcel"}
            <ArrowRight size={16} />
          </button>
        </footer>
      </form>
    </Modal>
  );
}
function ParcelDetails({ id, user, onClose, onUpdated, toast }) {
  const [detail, setDetail] = useState(null),
    [agents, setAgents] = useState([]),
    [agentId, setAgentId] = useState(""),
    [status, setStatus] = useState(""),
    [location, setLocation] = useState(""),
    [note, setNote] = useState(""),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  async function load() {
    try {
      const d = await request(`/parcels/${id}`);
      setDetail(d);
      setAgentId(d.parcel.agent?.id || "");
      setStatus(
        d.parcel.nextStatuses.filter(
          (s) => user.role === "ADMIN" || s !== "CANCELLED",
        )[0] || "",
      );
      if (user.role === "ADMIN") setAgents(await request("/agents"));
    } catch (e) {
      setError(e.message);
    }
  }
  useEffect(() => {
    load();
  }, [id]);
  async function action(path, body) {
    setBusy(true);
    setError("");
    try {
      await request(`/parcels/${id}/${path}`, { method: "PATCH", body });
      await load();
      onUpdated();
      toast(
        path === "assignment"
          ? "Delivery agent assigned"
          : "Shipment status updated",
      );
      setLocation("");
      setNote("");
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  const p = detail?.parcel,
    next =
      p?.nextStatuses.filter(
        (s) => user.role === "ADMIN" || s !== "CANCELLED",
      ) || [];
  async function copy() {
    try {
      await navigator.clipboard.writeText(
        `${window.location.origin}/?id=${encodeURIComponent(p.trackingId)}#track`,
      );
      toast("Tracking link copied");
    } catch {
      toast("Could not copy. Tracking ID: " + p.trackingId);
    }
  }
  return (
    <Modal
      title={p?.trackingId || "Shipment details"}
      subtitle="Manage this parcel and follow its journey."
      onClose={onClose}
    >
      <ErrorNote message={error} />
      {!detail ? (
        <div className="loading">Loading shipment…</div>
      ) : (
        <>
          <div className="detail-status">
            <Badge status={p.status} />
            <button className="text-button" onClick={copy}>
              <Copy size={14} />
              Copy tracking link
            </button>
          </div>
          <div className="contact-grid">
            <section>
              <p className="eyebrow">SENDER</p>
              <h3>{p.senderName}</h3>
              <p>
                {p.senderAddress}, {p.senderCity}
              </p>
              <p>{p.senderEmail}</p>
              <p>{p.senderPhone}</p>
            </section>
            <section>
              <p className="eyebrow">RECIPIENT</p>
              <h3>{p.recipientName}</h3>
              <p>
                {p.recipientAddress}, {p.recipientCity}
              </p>
              <p>{p.recipientEmail}</p>
              <p>{p.recipientPhone}</p>
            </section>
          </div>
          <div className="detail-meta">
            <span>{p.description}</span>
            <span>
              {p.weightKg} kg · {label(p.serviceType)}
            </span>
            <span>Expected {date(p.expectedDelivery)}</span>
          </div>
          {user.role === "ADMIN" && p.nextStatuses.length > 0 && (
            <form
              className="assignment-form"
              onSubmit={(e) => {
                e.preventDefault();
                action("assignment", { agentId: Number(agentId) });
              }}
            >
              <label className="field">
                <span>Delivery agent</span>
                <select
                  aria-label="Delivery agent"
                  value={agentId}
                  onChange={(e) => setAgentId(e.target.value)}
                  required
                >
                  <option value="">Choose an agent</option>
                  {agents.map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.name}
                    </option>
                  ))}
                </select>
              </label>
              <button
                className="button outline"
                disabled={
                  busy || !agentId || String(p.agent?.id) === String(agentId)
                }
              >
                Assign agent
              </button>
            </form>
          )}
          {next.length > 0 && (
            <form
              className="status-form"
              onSubmit={(e) => {
                e.preventDefault();
                action("status", { status, location, note });
              }}
            >
              <h3 className="subheading">Update delivery status</h3>
              {!p.agent && (
                <p className="muted">
                  Assign an agent before progressing a delivery. An admin can
                  cancel a registered parcel.
                </p>
              )}
              <div className="form-grid">
                <label className="field">
                  <span>Next status</span>
                  <select
                    aria-label="Next status"
                    value={status}
                    onChange={(e) => setStatus(e.target.value)}
                    required
                  >
                    {next.map((s) => (
                      <option key={s} value={s}>
                        {label(s)}
                      </option>
                    ))}
                  </select>
                </label>
                <Field
                  name="location"
                  label="Current city or hub"
                  value={location}
                  onChange={(e) => setLocation(e.target.value)}
                  maxLength={100}
                />
              </div>
              <Field
                name="note"
                label="Delivery note"
                value={note}
                onChange={(e) => setNote(e.target.value)}
                maxLength={500}
              />
              <button
                className="button primary"
                disabled={busy || (!p.agent && status !== "CANCELLED")}
              >
                {busy ? "Saving…" : "Save status update"}
                <Check size={16} />
              </button>
            </form>
          )}
          <h3 className="subheading">Shipment journey</h3>
          <Timeline events={detail.history} />
        </>
      )}
    </Modal>
  );
}
function Agents({ agents, onChange }) {
  const [open, setOpen] = useState(false),
    [form, setForm] = useState({ name: "", email: "", password: "" }),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await request("/agents", { method: "POST", body: form });
      onChange();
      setOpen(false);
      setForm({ name: "", email: "", password: "" });
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">THE PEOPLE BEHIND THE PARCELS</p>
          <h1>Delivery team</h1>
          <p>Give every shipment a person to count on.</p>
        </div>
        <button
          className="button primary"
          onClick={() => {
            setError("");
            setOpen(true);
          }}
        >
          <Plus size={17} />
          Add agent
        </button>
      </div>
      <div className="agent-grid">
        {agents.map((a) => (
          <article className="panel agent-card" key={a.id}>
            <span className="avatar">
              {a.name
                .split(" ")
                .map((x) => x[0])
                .join("")}
            </span>
            <h3>{a.name}</h3>
            <p>{a.email}</p>
            <span className="tag">
              <span className="green-dot" />
              Active delivery agent
            </span>
          </article>
        ))}
      </div>
      {!agents.length && (
        <Empty
          title="Build your delivery team"
          text="Add an agent to start assigning parcels."
        />
      )}
      {open && (
        <Modal
          title="Add delivery agent"
          subtitle="Create an account for a member of your team."
          onClose={() => setOpen(false)}
        >
          <form onSubmit={submit}>
            <ErrorNote message={error} />
            {["name", "email", "password"].map((n) => (
              <Field
                key={n}
                name={n}
                label={n === "name" ? "Full name" : label(n)}
                type={n === "name" ? "text" : n}
                value={form[n]}
                onChange={(e) => setForm({ ...form, [n]: e.target.value })}
                minLength={n === "password" ? 12 : undefined}
                maxLength={n === "password" ? 72 : n === "name" ? 100 : 254}
                autoComplete={n === "password" ? "new-password" : undefined}
              />
            ))}
            <p className="muted">
              Use a unique password of at least 12 characters. Share it securely
              with the agent.
            </p>
            <footer className="modal-actions">
              <button
                type="button"
                className="button outline"
                onClick={() => setOpen(false)}
              >
                Cancel
              </button>
              <button className="button primary" disabled={busy}>
                {busy ? "Creating…" : "Create agent"}
              </button>
            </footer>
          </form>
        </Modal>
      )}
    </>
  );
}
export default function App() {
  const [user, setUser] = useState(null),
    [boot, setBoot] = useState(true),
    [page, setPage] = useState(
      window.location.hash === "#track" ? "tracking" : "overview",
    ),
    [error, setError] = useState(""),
    [mobile, setMobile] = useState(false);
  const [dashboard, setDashboard] = useState(null),
    [list, setList] = useState({
      content: [],
      totalElements: 0,
      totalPages: 0,
    }),
    [agents, setAgents] = useState([]),
    [notifications, setNotifications] = useState([]);
  const [search, setSearch] = useState(""),
    [status, setStatus] = useState(""),
    [index, setIndex] = useState(0),
    [loading, setLoading] = useState(false),
    [refresh, setRefresh] = useState(0),
    [selected, setSelected] = useState(null),
    [creating, setCreating] = useState(false),
    [toastMessage, setToastMessage] = useState("");
  const fetchGeneration = useRef(0),
    toastTimer = useRef();
  const unread = notifications.filter((n) => !n.read).length;
  function toast(message) {
    setToastMessage(message);
    clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToastMessage(""), 4500);
  }
  function navigate(p) {
    setPage(p);
    setMobile(false);
    setError("");
    window.location.hash = p === "tracking" ? "track" : p;
  }
  useEffect(() => {
    request("/auth/me")
      .then(setUser)
      .catch((e) => {
        if (e.status !== 401)
          setError("The API is unavailable. Start the backend and try again.");
      })
      .finally(() => setBoot(false));
    const hash = () => {
      const h = window.location.hash.slice(1);
      if (h === "track") setPage("tracking");
    };
    window.addEventListener("hashchange", hash);
    return () => {
      window.removeEventListener("hashchange", hash);
      clearTimeout(toastTimer.current);
    };
  }, []);
  useEffect(() => {
    if (!user) return;
    let alive = true;
    async function inbox() {
      try {
        const n = await request("/notifications");
        if (alive) setNotifications(n);
      } catch {}
    }
    inbox();
    const timer = setInterval(inbox, 30000);
    return () => {
      alive = false;
      clearInterval(timer);
    };
  }, [user, refresh]);
  useEffect(() => {
    if (!user || page === "tracking" || page === "notifications") return;
    const generation = ++fetchGeneration.current;
    let disposed = false;
    setLoading(true);
    setError("");
    const timer = setTimeout(
      async () => {
        try {
          if (page === "overview") {
            const d = await request("/dashboard");
            if (!disposed && generation === fetchGeneration.current)
              setDashboard(d);
          } else if (page === "shipments") {
            const q = new URLSearchParams({ page: index, size: 10, search });
            if (status) q.set("status", status);
            const l = await request(`/parcels?${q}`);
            if (!disposed && generation === fetchGeneration.current) setList(l);
          } else if (page === "agents") {
            const a = await request("/agents");
            if (!disposed && generation === fetchGeneration.current)
              setAgents(a);
          }
        } catch (e) {
          if (!disposed) {
            if (e.status === 401) setUser(null);
            setError(e.message);
          }
        } finally {
          if (!disposed) setLoading(false);
        }
      },
      page === "shipments" ? 250 : 0,
    );
    return () => {
      disposed = true;
      clearTimeout(timer);
    };
  }, [user, page, index, search, status, refresh]);
  async function logout() {
    try {
      await request("/auth/logout", { method: "POST" });
      setUser(null);
      setDashboard(null);
      setNotifications([]);
      navigate("overview");
    } catch (e) {
      toast(e.message);
    }
  }
  async function read(n) {
    if (n.read) return;
    try {
      await request(`/notifications/${n.id}/read`, { method: "PATCH" });
      setRefresh((x) => x + 1);
    } catch (e) {
      toast(e.message);
    }
  }
  if (boot)
    return (
      <div className="boot">
        <Package size={32} />
        <p>Opening your workspace…</p>
      </div>
    );
  if (!user)
    return page === "tracking" ? (
      <Tracking onBack={() => navigate("overview")} />
    ) : (
      <>
        <ErrorNote message={error} />
        <Login
          onLogin={(u) => {
            setUser(u);
            navigate("overview");
          }}
          onTrack={() => navigate("tracking")}
        />
      </>
    );
  const nav = [
    { id: "overview", icon: LayoutDashboard, label: "Overview" },
    {
      id: "shipments",
      icon: Package,
      label: user.role === "ADMIN" ? "All shipments" : "My deliveries",
    },
    { id: "tracking", icon: Search, label: "Track a parcel" },
    ...(user.role === "ADMIN"
      ? [{ id: "agents", icon: Users, label: "Delivery team" }]
      : []),
    { id: "notifications", icon: Bell, label: "Notifications" },
  ];
  const deliveryRate = dashboard?.total
    ? Math.round((dashboard.delivered / dashboard.total) * 100)
    : 0;
  return (
    <div className="app-shell">
      {mobile && (
        <div className="sidebar-backdrop" onClick={() => setMobile(false)} />
      )}
      <aside className={`sidebar ${mobile ? "mobile-open" : ""}`}>
        <a
          href="#overview"
          onClick={(e) => {
            e.preventDefault();
            navigate("overview");
          }}
          className="brand"
        >
          <span className="brand-mark">
            <Package size={22} />
          </span>
          <span>
            courier<span className="brand-light">tracker</span>
          </span>
        </a>
        <div className="workspace-label">
          <span className="green-dot" />
          Operations workspace
        </div>
        <p className="nav-label">WORKSPACE</p>
        <nav>
          {nav.map(({ id, icon: Icon, label }) => (
            <button
              key={id}
              className={`nav-item ${page === id ? "active" : ""}`}
              onClick={() => navigate(id)}
            >
              <Icon size={19} />
              <span>{label}</span>
              {id === "notifications" && unread > 0 && <b>{unread}</b>}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="sidebar-note">
            <span className="note-icon">
              <ShieldCheck size={20} />
            </span>
            <h3>Every parcel matters.</h3>
            <p>
              Keep your deliveries moving,
              <br />
              one update at a time.
            </p>
          </div>
          <div className="user-panel">
            <span className="avatar">
              {user.name
                .split(" ")
                .map((x) => x[0])
                .join("")}
            </span>
            <div>
              <strong>{user.name}</strong>
              <small>
                {user.role === "ADMIN" ? "Administrator" : "Delivery agent"}
              </small>
            </div>
            <button
              aria-label="Sign out"
              onClick={logout}
              className="icon-button"
            >
              <LogOut size={17} />
            </button>
          </div>
        </div>
      </aside>
      <div className="main-shell">
        <header className="topbar">
          <div className="breadcrumb">
            <button
              className="icon-button mobile-toggle"
              aria-label="Open navigation"
              onClick={() => setMobile(!mobile)}
            >
              <Menu />
            </button>
            <span>Workspace</span>
            <ChevronRight size={13} />
            <strong>
              {nav.find((n) => n.id === page)?.label || "Overview"}
            </strong>
          </div>
          <div className="topbar-right">
            <span className="system-live">
              <span className="green-dot" />
              {user.role === "ADMIN" ? "Admin access" : "Agent access"}
            </span>
            <button
              aria-label="View notifications"
              className="icon-button notification-bell"
              onClick={() => navigate("notifications")}
            >
              <Bell size={20} />
              {unread > 0 && <i />}
            </button>
            <span className="avatar small">
              {user.name
                .split(" ")
                .map((x) => x[0])
                .join("")}
            </span>
          </div>
        </header>
        <main className="main-content">
          <ErrorNote message={error} />
          {page === "overview" && (
            <>
              <div className="page-heading">
                <div>
                  <p className="eyebrow">A CLEAR VIEW OF WHAT’S MOVING</p>
                  <h1>
                    Your delivery overview<span className="heading-dot">.</span>
                  </h1>
                  <p>
                    Welcome back, {user.name.split(" ")[0]}. Here’s the latest
                    across your shipments.
                  </p>
                </div>
                {user.role === "ADMIN" && (
                  <button
                    className="button primary"
                    onClick={() => setCreating(true)}
                  >
                    <Plus size={17} />
                    Register parcel
                  </button>
                )}
              </div>
              <div className="stats-grid">
                {[
                  {
                    title: "Total shipments",
                    value: dashboard?.total,
                    icon: Package,
                    text: "Every parcel in your workspace",
                  },
                  {
                    title: "On the move",
                    value: dashboard?.active,
                    icon: Truck,
                    text: "Active deliveries in progress",
                  },
                  {
                    title: "Delivered",
                    value: dashboard?.delivered,
                    icon: CheckCircle2,
                    text: "Safely at their destination",
                  },
                  {
                    title: "Needs attention",
                    value: dashboard?.exceptions,
                    icon: AlertCircle,
                    text: "Unsuccessful delivery attempts",
                    orange: true,
                  },
                ].map(({ title, value, icon: Icon, text, orange }) => (
                  <article
                    className={`stat-card ${orange ? "attention" : ""}`}
                    key={title}
                  >
                    <div>
                      <span>{title}</span>
                      <span className="stat-icon">
                        <Icon size={19} />
                      </span>
                    </div>
                    <strong>{value ?? "—"}</strong>
                    <p>{text}</p>
                  </article>
                ))}
              </div>
              <div className="overview-grid">
                <section className="journey-panel panel">
                  <div className="section-head">
                    <div>
                      <p className="eyebrow">FROM PICKUP TO HANDOFF</p>
                      <h2>Delivery pulse</h2>
                    </div>
                    <span className="tag">
                      <span className="green-dot" />
                      Current snapshot
                    </span>
                  </div>
                  <div className="pulse-body">
                    <div
                      className="delivery-ring"
                      style={{ "--progress": `${deliveryRate * 3.6}deg` }}
                    >
                      <div>
                        <strong>
                          {deliveryRate}
                          <small>%</small>
                        </strong>
                        <span>delivered</span>
                      </div>
                    </div>
                    <div className="pulse-legend">
                      {[
                        {
                          s: "REGISTERED",
                          name: "Registered",
                          color: "#8aa6a0",
                        },
                        {
                          s: "IN_TRANSIT",
                          name: "In transit",
                          color: "#578cbb",
                        },
                        {
                          s: "OUT_FOR_DELIVERY",
                          name: "Out for delivery",
                          color: "#b18a50",
                        },
                        { s: "DELIVERED", name: "Delivered", color: "#367861" },
                      ].map((x) => (
                        <div key={x.s}>
                          <span>
                            <i style={{ background: x.color }} />
                            {x.name}
                          </span>
                          <strong>{dashboard?.byStatus[x.s] ?? "—"}</strong>
                        </div>
                      ))}
                    </div>
                  </div>
                </section>
                <section className="operations-panel">
                  <div className="operations-icon">
                    <Truck size={25} />
                  </div>
                  <p className="eyebrow">A LITTLE CHECK-IN</p>
                  <h2>
                    Keep the day
                    <br />
                    moving forward.
                  </h2>
                  <p>
                    <strong>{dashboard?.unassigned ?? "—"}</strong> active
                    parcels need an agent.
                    <br />
                    <strong>{dashboard?.overdue ?? "—"}</strong> active parcels
                    are past their expected date.
                  </p>
                  <button
                    onClick={() => {
                      setStatus("");
                      setIndex(0);
                      navigate("shipments");
                    }}
                  >
                    Review shipments <ArrowUpRight size={17} />
                  </button>
                </section>
              </div>
              <section className="panel recent-panel">
                <div className="section-head">
                  <div>
                    <h2>Recent shipments</h2>
                    <p>The latest parcels in your workspace.</p>
                  </div>
                  <button
                    className="text-button"
                    onClick={() => navigate("shipments")}
                  >
                    View all shipments <ArrowRight size={16} />
                  </button>
                </div>
                <ParcelTable
                  parcels={dashboard?.recent || []}
                  onSelect={setSelected}
                  loading={loading}
                />
              </section>
            </>
          )}
          {page === "shipments" && (
            <>
              <div className="page-heading">
                <div>
                  <p className="eyebrow">EVERY JOURNEY, IN ONE PLACE</p>
                  <h1>
                    {user.role === "ADMIN" ? "All shipments" : "My deliveries"}
                    <span className="heading-dot">.</span>
                  </h1>
                  <p>
                    {user.role === "ADMIN"
                      ? "Register, assign, and manage your delivery workflow."
                      : "Your assigned parcels and the next steps for each delivery."}
                  </p>
                </div>
                {user.role === "ADMIN" && (
                  <button
                    className="button primary"
                    onClick={() => setCreating(true)}
                  >
                    <Plus size={17} />
                    Register parcel
                  </button>
                )}
              </div>
              <section className="panel">
                <div className="filters">
                  <label className="search-field">
                    <Search size={18} />
                    <input
                      aria-label="Search shipments"
                      placeholder="Search tracking ID, recipient, or city…"
                      value={search}
                      onChange={(e) => {
                        setSearch(e.target.value);
                        setIndex(0);
                      }}
                    />
                  </label>
                  <select
                    aria-label="Filter by status"
                    value={status}
                    onChange={(e) => {
                      setStatus(e.target.value);
                      setIndex(0);
                    }}
                  >
                    <option value="">All statuses</option>
                    {statuses.map((s) => (
                      <option key={s} value={s}>
                        {label(s)}
                      </option>
                    ))}
                  </select>
                  <button
                    className="icon-button"
                    aria-label="Refresh shipments"
                    onClick={() => setRefresh((x) => x + 1)}
                  >
                    <RefreshCw size={18} />
                  </button>
                </div>
                <ParcelTable
                  parcels={list.content}
                  onSelect={setSelected}
                  loading={loading}
                />
                <footer className="pagination">
                  <span>
                    {list.totalElements} shipment
                    {list.totalElements === 1 ? "" : "s"} · Page{" "}
                    {list.totalPages ? index + 1 : 0} of {list.totalPages}
                  </span>
                  <div>
                    <button
                      className="button outline"
                      disabled={index === 0 || loading}
                      onClick={() => setIndex(index - 1)}
                    >
                      <ChevronLeft size={15} />
                      Previous
                    </button>
                    <button
                      className="button outline"
                      disabled={index + 1 >= list.totalPages || loading}
                      onClick={() => setIndex(index + 1)}
                    >
                      Next
                      <ChevronRight size={15} />
                    </button>
                  </div>
                </footer>
              </section>
            </>
          )}
          {page === "tracking" && <Tracking compact />}
          {page === "agents" && (
            <Agents
              agents={agents}
              onChange={() => {
                setRefresh((x) => x + 1);
                toast("Agent account created");
              }}
            />
          )}
          {page === "notifications" && (
            <>
              <div className="page-heading">
                <div>
                  <p className="eyebrow">STAY IN THE LOOP</p>
                  <h1>
                    Notifications<span className="heading-dot">.</span>
                  </h1>
                  <p>
                    Assignments and delivery updates, right where you need them.
                  </p>
                </div>
                <button
                  className="button outline"
                  onClick={() => setRefresh((x) => x + 1)}
                >
                  <RefreshCw size={16} />
                  Refresh
                </button>
              </div>
              <section className="panel notification-list">
                {notifications.map((n) => (
                  <article className={n.read ? "" : "unread"} key={n.id}>
                    <span className="notification-icon">
                      <Bell size={19} />
                    </span>
                    <div>
                      <strong>{n.title}</strong>
                      <p>{n.message}</p>
                      <small>
                        {timestamp(n.createdAt)} · Email: {label(n.emailStatus)}
                      </small>
                    </div>
                    {!n.read && (
                      <button className="text-button" onClick={() => read(n)}>
                        Mark as read <Check size={15} />
                      </button>
                    )}
                  </article>
                ))}
                {!notifications.length && (
                  <Empty
                    title="You’re all caught up"
                    text="New assignments and delivery updates will appear here."
                  />
                )}
              </section>
              <p className="muted">
                Showing your latest 100 notifications. Updates refresh every 30
                seconds. Email requires SMTP configuration.
              </p>
            </>
          )}
          <footer className="workspace-footer">
            <span>Courier Tracker</span>
            <span>Thoughtfully tracked. Reliably delivered.</span>
          </footer>
        </main>
      </div>
      {creating && (
        <CreateParcel
          onClose={() => setCreating(false)}
          onCreated={(p) => {
            setCreating(false);
            setRefresh((x) => x + 1);
            setSelected(p.id);
            toast("Parcel registered · " + p.trackingId);
          }}
        />
      )}
      {selected && (
        <ParcelDetails
          id={selected}
          user={user}
          onClose={() => setSelected(null)}
          onUpdated={() => setRefresh((x) => x + 1)}
          toast={toast}
        />
      )}{" "}
      {toastMessage && (
        <div className="toast" role="status">
          <CheckCircle2 size={18} />
          {toastMessage}
          <button
            aria-label="Dismiss notification"
            onClick={() => setToastMessage("")}
          >
            <X size={15} />
          </button>
        </div>
      )}
    </div>
  );
}
