// IELTS Platform SPA. Vanilla JS, token in localStorage.
"use strict";

// ---------- State ----------
const state = {
  token: localStorage.getItem("token") || "",
  role: localStorage.getItem("role") || "",
  fullName: localStorage.getItem("fullName") || "",
  profile: null,
  progress: null,
  writingTasks: [],
  essays: [],
  speakingTopics: [],
  speakingSessions: [],
  students: [],
  criteria: [],
  // Essay editor
  editor: { taskId: null, startedAt: null, typingStart: null, typingMs: 0, pasteCount: 0, examMode: false, timer: null },
  // Speaking session
  speaking: { sessionId: null, topic: null, qaIndex: 0, answers: [], recognition: null, timer: null, answerStart: null },
};

// ---------- API ----------
async function api(method, path, body) {
  const opts = { method, headers: { "Content-Type": "application/json" } };
  if (state.token) opts.headers["Authorization"] = "Bearer " + state.token;
  if (body !== undefined) opts.body = JSON.stringify(body);
  const res = await fetch(path, opts);
  const isAuthPath = path.startsWith("/api/auth/");
  // 401 on auth = wrong credentials; 401 on other = expired token
  if (res.status === 401 && !isAuthPath) { logout(); throw new Error("Session expired"); }
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) throw new Error((data && (data.message || data.error)) || ("HTTP " + res.status));
  return data;
}
const GET = (p) => api("GET", p);
const POST = (p, b) => api("POST", p, b);
const PUT = (p, b) => api("PUT", p, b);
const DEL = (p) => api("DELETE", p);

// ---------- Utilities ----------
const $ = (sel) => document.querySelector(sel);
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) =>
  ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

function toast(msg, isError = false) {
  const t = $("#toast");
  t.textContent = msg;
  t.className = isError ? "error" : "";
  clearTimeout(t._tm);
  t._tm = setTimeout(() => t.classList.add("hidden"), 3500);
}

// Simplest markdown → HTML (headings, lists, **bold**, `code`, blockquotes)
function md(text) {
  if (!text) return "";
  const lines = esc(text).split("\n");
  let html = "", inList = false;
  const closeList = () => { if (inList) { html += "</ul>"; inList = false; } };
  for (const line of lines) {
    const l = line.trim();
    if (/^###\s/.test(l)) { closeList(); html += `<h3>${l.slice(4)}</h3>`; }
    else if (/^##\s/.test(l)) { closeList(); html += `<h2>${l.slice(3)}</h2>`; }
    else if (/^#\s/.test(l)) { closeList(); html += `<h2>${l.slice(2)}</h2>`; }
    else if (/^[-*]\s/.test(l)) { if (!inList) { html += "<ul>"; inList = true; } html += `<li>${l.slice(2)}</li>`; }
    else if (/^>\s?/.test(l)) { closeList(); html += `<blockquote>${l.replace(/^>\s?/, "")}</blockquote>`; }
    else if (l === "") { closeList(); }
    else { closeList(); html += `<p>${l}</p>`; }
  }
  closeList();
  return html
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
    .replace(/`([^`]+)`/g, "<code>$1</code>");
}

function showCriteriaModal(title, version, content) {
  const existing = document.getElementById("criteria-modal");
  if (existing) existing.remove();
  const modal = document.createElement("div");
  modal.id = "criteria-modal";
  modal.className = "card";
  modal.style = "position:fixed;top:50%;left:50%;transform:translate(-50%,-50%);max-height:80vh;overflow:auto;width:min(800px,92vw);z-index:1000;box-shadow:0 10px 30px rgba(0,0,0,0.2);background:var(--surface);border:1px solid var(--border);";
  modal.innerHTML = `
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
      <h2 style="margin:0">${esc(title)} <span class="muted small">v${version}</span></h2>
      <button class="btn small" id="cr-modal-close">Close</button>
    </div>
    <div class="report">${md(content)}</div>`;
  document.body.appendChild(modal);
  const overlay = document.createElement("div");
  overlay.id = "criteria-overlay";
  overlay.style = "position:fixed;inset:0;background:rgba(0,0,0,0.4);z-index:999;";
  overlay.onclick = () => { modal.remove(); overlay.remove(); };
  document.body.appendChild(overlay);
  document.getElementById("cr-modal-close").onclick = () => { modal.remove(); overlay.remove(); };
}

// IELTS rounding on the client (for live preview)
function ieltsRound(band) {
  const whole = Math.floor(band), frac = band - whole;
  if (frac < 0.25) return whole;
  if (frac < 0.75) return whole + 0.5;
  return whole + 1;
}

function fmtBand(v) { return v == null ? "—" : Number(v).toFixed(1); }

// ---------- Auth ----------
function logout() {
  localStorage.removeItem("token"); localStorage.removeItem("role"); localStorage.removeItem("fullName");
  state.token = ""; state.role = "";
  $("#app").classList.add("hidden");
  $("#auth-screen").classList.remove("hidden");
}

async function boot() {
  if (!state.token) return;
  try {
    state.profile = await GET("/api/profile");
    state.role = state.profile.role;
    showApp();
    await loadAll();
    renderPage("dashboard");
  } catch (e) { /* profile unavailable — logout on 401 inside api() */ }
}

function showApp() {
  $("#auth-screen").classList.add("hidden");
  $("#app").classList.remove("hidden");
  $("#nav-user").textContent = state.fullName || state.profile?.email || "";
  $("#nav-cabinet").classList.toggle("hidden", state.role !== "TEACHER");
}

async function loadAll() {
  const jobs = [
    GET("/api/results/progress").then((d) => (state.progress = d)),
    GET("/api/writing/tasks").then((d) => (state.writingTasks = d)),
    GET("/api/essays").then((d) => (state.essays = d)),
    GET("/api/speaking/topics").then((d) => (state.speakingTopics = d)),
    GET("/api/speaking/sessions").then((d) => (state.speakingSessions = d)),
  ];
  if (state.role === "TEACHER") {
    jobs.push(GET("/api/admin/students").then((d) => (state.students = d)));
    jobs.push(GET("/api/writing/criteria").then((d) => (state.criteria = d)));
  }
  await Promise.allSettled(jobs);
}

// ---------- Navigation ----------
const pages = ["dashboard", "tracker", "writing", "speaking", "cabinet"];
function renderPage(name) {
  pages.forEach((p) => $(`#page-${p}`).classList.toggle("hidden", p !== name));
  document.querySelectorAll(".nav-btn").forEach((b) => b.classList.toggle("active", b.dataset.page === name));
  ({ dashboard: renderDashboard, tracker: renderTracker, writing: renderWriting,
     speaking: renderSpeaking, cabinet: renderCabinet })[name]();
}

// ---------- Dashboard ----------
function renderDashboard() {
  const p = state.progress;
  const el = $("#page-dashboard");
  if (!p || p.count === 0) {
    el.innerHTML = `<div class="card hero">
      <h2>Welcome! 👋</h2>
      <p class="muted">Your IELTS preparation progress will appear here. Add your first test result to get started!</p>
      <button class="btn" style="background:rgba(255,255,255,.92);margin-top:14px" data-goto="tracker">📈 Add first result</button>
    </div>`;
    el.querySelector("[data-goto]").onclick = () => renderPage("tracker");
    return;
  }
  const last = p.series[p.series.length - 1];
  const prev = p.series.length > 1 ? p.series[p.series.length - 2] : null;
  const delta = prev && prev.overall != null && last.overall != null
    ? last.overall - prev.overall : null;
  const target = p.targetBand;
  const gap = target && last.overall != null ? (last.overall - target) : null;

  el.innerHTML = `
    <div class="grid cols-4">
      <div class="card stat"><div class="value">${fmtBand(last.overall)}</div><div class="label">Latest Overall</div>
        ${delta != null ? `<div class="delta ${delta >= 0 ? "up" : "down"}">${delta >= 0 ? "▲" : "▼"} ${Math.abs(delta).toFixed(1)} from previous</div>` : ""}</div>
      <div class="card stat"><div class="value">${fmtBand(p.bestOverall)}</div><div class="label">Best Overall</div></div>
      <div class="card stat"><div class="value">${target ? target.toFixed(1) : "—"}</div><div class="label">Target</div>
        ${gap != null ? `<div class="delta ${gap >= 0 ? "up" : "down"}">${gap >= 0 ? "target reached" : "missing " + Math.abs(gap).toFixed(1)}</div>` : ""}</div>
      <div class="card stat"><div class="value">${p.count}</div><div class="label">Tests recorded</div></div>
    </div>
    <div class="card"><h2>Overall Progress</h2>${overallChart(p.series, target)}</div>
    <div class="card"><h2>Latest Test (${esc(last.date)})</h2>${skillBars(last)}</div>`;
}

function overallChart(series, target) {
  const W = 900, H = 260, pad = { l: 34, r: 10, t: 14, b: 26 };
  const iw = W - pad.l - pad.r, ih = H - pad.t - pad.b;
  const y = (v) => pad.t + ih - ((v - 4) / 5) * ih;   // scale 4–9
  const x = (i) => pad.l + (series.length === 1 ? iw / 2 : (i / (series.length - 1)) * iw);
  const pts = series.map((s, i) => s.overall == null ? null : [x(i), y(s.overall)]);
  const path = pts.filter(Boolean).map((p, i) => (i === 0 ? "M" : "L") + p[0].toFixed(1) + "," + p[1].toFixed(1)).join(" ");
  let svg = `<svg class="chart-svg" viewBox="0 0 ${W} ${H}">`;
  for (let v = 4; v <= 9; v += 0.5) {
    svg += `<line x1="${pad.l}" y1="${y(v)}" x2="${W - pad.r}" y2="${y(v)}"
      stroke="var(--border)" stroke-width="${v % 1 === 0 ? 1 : 0.5}"/>
      ${v % 1 === 0 ? `<text x="${pad.l - 6}" y="${y(v) + 4}" text-anchor="end" font-size="11" fill="var(--muted)">${v}</text>` : ""}`;
  }
  if (target) svg += `<line x1="${pad.l}" y1="${y(target)}" x2="${W - pad.r}" y2="${y(target)}"
    stroke="var(--success)" stroke-dasharray="6 4"/><text x="${W - pad.r}" y="${y(target) - 5}" text-anchor="end" font-size="11" fill="var(--success)">target ${target}</text>`;
  if (path) svg += `<path d="${path}" fill="none" stroke="var(--accent)" stroke-width="2.5" stroke-linejoin="round"/>`;
  pts.filter(Boolean).forEach((p, i) => {
    svg += `<circle cx="${p[0]}" cy="${p[1]}" r="4" fill="var(--accent)"/>
      <title>${series[i].date}: ${fmtBand(series[i].overall)}</title>`;
  });
  series.forEach((s, i) => {
    if (series.length <= 10 || i % Math.ceil(series.length / 10) === 0)
      svg += `<text x="${x(i)}" y="${H - 8}" text-anchor="middle" font-size="10" fill="var(--muted)">${s.date.slice(5)}</text>`;
  });
  return svg + "</svg>";
}

function skillBars(r) {
  const skills = [["Listening", r.listening], ["Reading", r.reading], ["Writing", r.writing], ["Speaking", r.speaking]];
  return skills.map(([name, v]) => `
    <div style="display:flex;align-items:center;gap:10px;margin:6px 0">
      <span style="width:90px;font-size:13px">${name}</span>
      <div style="flex:1;background:var(--bg);border-radius:6px;height:18px;overflow:hidden">
        <div style="width:${((v || 0) / 9) * 100}%;height:100%;background:${v == null ? "var(--border)" : "var(--accent)"}"></div>
      </div>
      <span style="width:32px;text-align:right;font-weight:600">${fmtBand(v)}</span>
    </div>`).join("") + `
    <div style="display:flex;align-items:center;gap:10px;margin:6px 0">
      <span style="width:90px;font-size:13px;font-weight:700">Overall</span>
      <div style="flex:1;background:var(--bg);border-radius:6px;height:18px;overflow:hidden">
        <div style="width:${((r.overall || 0) / 9) * 100}%;height:100%;background:var(--success)"></div>
      </div>
      <span style="width:32px;text-align:right;font-weight:700">${fmtBand(r.overall)}</span>
    </div>`;
}

// ---------- Tracker ----------
function renderTracker() {
  const el = $("#page-tracker");
  const rows = state.progress ? state.progress.series : [];
  el.innerHTML = `
    <div class="card">
      <h2>Add Test Result</h2>
      ${state.role === "TEACHER" ? `
      <div><label>Student</label>
      <select id="tr-student">
        ${(state.students || []).map((s) => `<option value="${s.id}">${esc(s.fullName || s.email)}${s.targetBand ? " · target " + s.targetBand : ""}</option>`).join("")}
      </select></div>` : ""}
      <div class="grid cols-2">
        <div><label>Test Date</label><input type="date" id="tr-date" value="${new Date().toISOString().slice(0, 10)}"></div>
        <div><label>Test Type</label><select id="tr-type"><option>ACADEMIC</option><option>GENERAL</option></select></div>
      </div>
      <div class="skills-input">
        ${["listening", "reading", "writing", "speaking"].map((s) => `
          <div><label>${s[0].toUpperCase() + s.slice(1)}</label>
          <select id="tr-${s}"><option value="">—</option>
          ${Array.from({ length: 17 }, (_, i) => (i + 2) / 2).map((v) => `<option value="${v}">${v.toFixed(1)}</option>`).join("")}
          </select></div>`).join("")}
      </div>
      <div class="preview" id="tr-preview">Overall: — (need all 4 skills)</div>
      <div class="grid cols-2">
        <div><label>Source (e.g., Cambridge 19, Test 2)</label><input id="tr-source" style="width:100%"></div>
        <div><label>Comment</label><input id="tr-comment" style="width:100%"></div>
      </div>
      <button class="btn primary" id="tr-save">💾 Save result</button>
    </div>
    <div class="card">
      <h2>History <button class="btn small" id="tr-export" style="float:right">Export CSV</button></h2>
      <table><thead><tr><th>Date</th><th>L</th><th>R</th><th>W</th><th>S</th><th>Overall</th><th>Source</th><th></th></tr></thead>
      <tbody>${rows.slice().reverse().map((r) => `
        <tr><td>${esc(r.date)}</td><td>${fmtBand(r.listening)}</td><td>${fmtBand(r.reading)}</td>
        <td>${fmtBand(r.writing)}</td><td>${fmtBand(r.speaking)}</td><td><b>${fmtBand(r.overall)}</b></td>
        <td>${esc(r.source || "")}</td>
        <td><button class="btn small danger" data-del="${r.id}">✕</button></td></tr>`).join("")
        || `<tr><td colspan="8"><div class="empty"><span class="icon">📈</span><div class="title">History is empty</div><div class="hint">Enter practice test results — progress and growth chart will appear here.</div></div></td></tr>`}
      </tbody></table>
    </div>`;

  const updatePreview = () => {
    const vals = ["listening", "reading", "writing", "speaking"].map((s) => {
      const v = $(`#tr-${s}`).value;
      return v === "" ? null : parseFloat(v);
    });
    if (vals.some((v) => v == null)) {
      $("#tr-preview").textContent = "Overall: — (need all 4 skills)";
    } else {
      const avg = vals.reduce((a, b) => a + b, 0) / 4;
      $("#tr-preview").innerHTML = `Average: <b>${avg.toFixed(3)}</b> → Overall by IELTS rules: <b>${ieltsRound(avg).toFixed(1)}</b>`;
    }
  };
  ["listening", "reading", "writing", "speaking"].forEach((s) =>
    $(`#tr-${s}`).addEventListener("change", updatePreview));

  // Teacher: selected student (default first)
  const selectedStudentId = () => {
    const sel = $("#tr-student");
    return sel && state.role === "TEACHER" ? parseInt(sel.value) : null;
  };
  const refreshProgress = async () => {
    const sid = selectedStudentId();
    state.progress = await GET(sid ? `/api/results/progress?studentId=${sid}` : "/api/results/progress");
  };
  if ($("#tr-student")) {
    $("#tr-student").onchange = async () => { await refreshProgress(); renderTracker(); };
    // on first render load the selected student's progress without resetting already entered data:
    // only redraw the history table
    if (state.role === "TEACHER" && state.students?.length) {
      refreshProgress().then(() => {
        const tbody = el.querySelector("tbody");
        if (tbody) {
          const series = state.progress?.series || [];
          tbody.innerHTML = series.slice().reverse().map((r) => `
            <tr><td>${esc(r.date)}</td><td>${fmtBand(r.listening)}</td><td>${fmtBand(r.reading)}</td>
            <td>${fmtBand(r.writing)}</td><td>${fmtBand(r.speaking)}</td><td><b>${fmtBand(r.overall)}</b></td>
            <td>${esc(r.source || "")}</td>
            <td><button class="btn small danger" data-del="${r.id}">✕</button></td></tr>`).join("");
          bindDelete();
        }
      }).catch(() => {});
    }
  }

  $("#tr-save").onclick = async () => {
    const body = {
      testDate: $("#tr-date").value, examType: $("#tr-type").value,
      listening: numOrNull($("#tr-listening").value), reading: numOrNull($("#tr-reading").value),
      writing: numOrNull($("#tr-writing").value), speaking: numOrNull($("#tr-speaking").value),
      source: $("#tr-source").value, comment: $("#tr-comment").value,
      studentId: selectedStudentId(),
    };
    if (!body.testDate) return toast("Please select a date", true);
    const btn = $("#tr-save");
    btn.classList.add("loading"); btn.disabled = true;
    try {
      await POST("/api/results", body);
      toast("Result saved");
      await refreshProgress();
      renderTracker();
    } catch (e) { toast(e.message, true); }
    btn.classList.remove("loading"); btn.disabled = false;
  };

  $("#tr-export").onclick = () => {
    const sid = selectedStudentId();
    fetch("/api/results/export" + (sid ? "?studentId=" + sid : ""),
          { headers: { Authorization: "Bearer " + state.token } })
      .then((r) => r.blob()).then((b) => {
        const u = URL.createObjectURL(b);
        const link = document.createElement("a");
        link.href = u; link.download = "ielts-progress.csv"; link.click();
        URL.revokeObjectURL(u);
      });
  };

  const bindDelete = () => {
    el.querySelectorAll("[data-del]").forEach((btn) => {
      btn.onclick = async () => {
        const id = parseInt(btn.dataset.del);
        try {
          await DEL("/api/results/" + id);
          await refreshProgress();
          toast("Deleted");
          renderTracker();
        } catch (e) { toast(e.message, true); }
      };
    });
  };
  bindDelete();
}
const numOrNull = (v) => (v === "" || v == null ? null : parseFloat(v));

// ---------- Writing ----------
function renderWriting() {
  const el = $("#page-writing");
  const isTeacher = state.role === "TEACHER";
  el.innerHTML = `
    <div class="card">
      <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap;margin-bottom:8px">
        <h2 style="margin:0">Writing Tasks</h2>
        <span style="flex:1"></span>
        <button class="btn small" id="btn-view-criteria">View Assessment Criteria</button>
      </div>
      ${state.writingTasks.length === 0 ? `<div class="empty"><span class="icon">📝</span>
        <div class="title">No tasks yet</div>
        <div class="hint">${isTeacher ? "Create the first task in the teacher panel — it will appear for your students." : "The teacher hasn't added tasks yet. Check back later!"}</div>
        ${isTeacher ? `<button class="btn primary" data-page="cabinet">Create task → Teacher Panel</button>` : ""}</div>` : ""}
      <table><thead><tr><th>Type</th><th>Task</th><th>Limit</th><th></th></tr></thead><tbody>
      ${state.writingTasks.map((t) => `
        <tr><td>${t.taskType}<br><span class="muted small">${t.moduleType}${t.promptKind ? " · " + esc(t.promptKind) : ""}</span></td>
        <td style="max-width:420px">${esc(t.prompt.slice(0, 140))}${t.prompt.length > 140 ? "…" : ""}${t.imageUrl ? " 🖼️" : ""}</td>
        <td>${t.minWords} words<br><span class="muted small">${t.timeLimitMin} min</span></td>
        <td><button class="btn small primary" data-write="${t.id}">Write</button></td></tr>`).join("")}
      </tbody></table>
    </div>
    <div id="essay-editor"></div>
    <div class="card">
      <h2>My Checks / Essays</h2>
      <table><thead><tr><th>Date</th><th>Student</th><th>Type</th><th>Words</th><th>Attempt</th><th>Overall</th><th>Honesty</th><th></th></tr></thead><tbody>
      ${state.essays.map((e) => `
        <tr><td>${e.createdAt.slice(0, 10)}</td><td>${esc(e.studentName)}</td><td>${e.taskType}</td><td>${e.wordCount}</td>
        <td>${e.attempt}</td><td><b>${e.checked ? fmtBand(e.overall) : "—"}</b></td>
        <td>${honestyBadge(e)}</td>
        <td><button class="btn small" data-view-essay="${e.id}">📄 Open</button></td></tr>`).join("") || `<tr><td colspan="8"><div class="empty"><span class="icon">🗂</span><div class="title">No essays yet</div><div class="hint">Select a task above and write your first essay — AI will check it within seconds.</div></div></td></tr>`}
      </tbody></table>
    </div>`;

  el.querySelectorAll("[data-write]").forEach((b) => b.onclick = () => openEditor(parseInt(b.dataset.write)));
  el.querySelectorAll("[data-view-essay]").forEach((b) => b.onclick = () => viewEssay(parseInt(b.dataset.viewEssay)));
  el.querySelectorAll('[data-page="cabinet"]').forEach((b) => b.onclick = () => renderPage("cabinet"));
  const btnViewCriteria = $("#btn-view-criteria");
  if (btnViewCriteria) {
    btnViewCriteria.onclick = async () => {
      try {
        const cr = await GET("/api/writing/active-criteria");
        showCriteriaModal(cr.title, cr.version, cr.content);
      } catch (e) { toast(e.message, true); }
    };
  }
}

function honestyBadge(e) {
  const flags = [];
  if (e.pasteCount > 0) flags.push(`pastes: ${e.pasteCount}`);
  if (e.typingMs > 0 && e.wordCount > 0) {
    const wpm = e.wordCount / (e.typingMs / 60000);
    if (wpm > 80) flags.push(`speed ${wpm.toFixed(0)} wpm ⚠`);
  }
  return flags.length ? `<span class="small" style="color:var(--warning)">${flags.join(", ")}</span>`
    : `<span class="small" style="color:var(--success)">ok${e.examMode ? " · exam mode" : ""}</span>`;
}

function openEditor(taskId) {
  const task = state.writingTasks.find((t) => t.id === taskId);
  if (!task) return;
  const ed = state.editor;
  ed.taskId = taskId; ed.startedAt = Date.now(); ed.typingStart = null;
  ed.typingMs = 0; ed.pasteCount = 0; ed.examMode = false;

  $("#essay-editor").innerHTML = `
    <div class="card">
      <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap">
        <button class="btn small" id="ed-back">← Back to tasks</button>
        <h2 style="margin:0;flex:1">New Essay — ${task.taskType} (${task.moduleType})</h2>
      </div>
      <div class="banner" style="background:var(--bg);margin:12px 0">${esc(task.prompt)}</div>
      ${task.imageUrl ? `<img src="${esc(task.imageUrl)}" class="task-image" alt="Task image">` : ""}
      <div style="display:flex;justify-content:flex-end;margin:4px 0">
        <button class="btn small" id="ed-view-criteria">View Criteria</button>
      </div>
      <div class="essay-stats">
        <label style="margin:0"><input type="checkbox" id="ed-exam"> Exam Mode (${task.timeLimitMin} min timer)</label>
        <span class="timer" id="ed-timer">${task.timeLimitMin}:00</span>
      </div>
      <textarea id="essay-textarea" placeholder="Write your essay here..."></textarea>
      <div class="essay-stats">
        <span id="ed-words">0 words</span>
        <span class="muted small" id="ed-min">min ${task.minWords}</span>
        <span style="flex:1"></span>
        <button class="btn primary" id="ed-submit">✓ Submit for AI check</button>
      </div>
    </div>`;
  $("#ed-back").onclick = () => { clearInterval(ed.timer); $("#essay-editor").innerHTML = ""; };
  $("#ed-view-criteria").onclick = async () => {
    try {
      const cr = await GET("/api/writing/active-criteria");
      showCriteriaModal(cr.title, cr.version, cr.content);
    } catch (e) { toast(e.message, true); }
  };

  const ta = $("#essay-textarea");
  const updateWords = () => {
    const n = ta.value.trim() ? ta.value.trim().split(/\s+/).length : 0;
    $("#ed-words").innerHTML = `<span class="${n >= task.minWords ? "word-ok" : "word-low"}">${n} words</span>`;
  };
  ta.addEventListener("input", () => {
    if (!ed.typingStart) ed.typingStart = Date.now();
    updateWords();
  });
  ta.addEventListener("paste", () => { ed.pasteCount++; });

  $("#ed-exam").onchange = (ev) => {
    ed.examMode = ev.target.checked;
    clearInterval(ed.timer);
    if (ed.examMode) {
      let left = task.timeLimitMin * 60;
      const tick = () => {
        const m = Math.floor(left / 60), s = left % 60;
        const t = $("#ed-timer");
        if (t) { t.textContent = `${m}:${String(s).padStart(2, "0")}`; t.classList.toggle("expired", left <= 60); }
        if (left-- <= 0) { clearInterval(ed.timer); toast("Time is up! Submit your essay.", true); }
      };
      tick();
      ed.timer = setInterval(tick, 1000);
    } else {
      $("#ed-timer").textContent = task.timeLimitMin + ":00";
    }
  };

  $("#ed-submit").onclick = async () => {
    const text = ta.value.trim();
    if (!text) return toast("Essay is empty", true);
    if (ed.typingStart) ed.typingMs = Date.now() - ed.typingStart;
    const btn = $("#ed-submit");
    btn.classList.add("loading"); btn.disabled = true;
    try {
      const res = await POST("/api/essays", {
        taskId: ed.taskId, text, typingMs: ed.typingMs,
        pasteCount: ed.pasteCount, examMode: ed.examMode,
      });
      state.essays = await GET("/api/essays");
      clearInterval(ed.timer);
      $("#essay-editor").innerHTML = "";
      if (res.belowMinWords) toast(`Attention: ${res.wordCount} words — below limit ${task.minWords}`, true);
      if (res.checkError) {
        toast("Essay saved, but AI check failed: " + res.checkError, true);
      } else {
        toast("Check complete");
        viewEssay(res.essayId);
      }
      renderWriting();
    } catch (e) {
      toast(e.message, true);
      btn.classList.remove("loading"); btn.disabled = false; btn.textContent = "✓ Submit for AI check";
    }
  };
  ta.focus();
}

async function viewEssay(id) {
  let data;
  try { data = await GET("/api/essays/" + id); } catch (e) { return toast(e.message, true); }
  const isTeacher = state.role === "TEACHER";
  const el = document.createElement("div");
  el.className = "card";
  el.innerHTML = `
    <h2>Essay #${data.id} — ${data.taskType}, attempt ${data.attempt}</h2>
    <p class="muted small">${esc(data.studentName)} · ${data.wordCount} words · ${honestyBadge(data)}</p>
    ${data.imageUrl ? `<img src="${esc(data.imageUrl)}" class="task-image" alt="Task image">` : ""}
    ${data.report ? `
      <div class="scores">
        ${scoreCell("TR", data.final?.taskResponse ?? data.ai?.taskResponse)}
        ${scoreCell("CC", data.final?.coherence ?? data.ai?.coherence)}
        ${scoreCell("LR", data.final?.lexical ?? data.ai?.lexical)}
        ${scoreCell("GRA", data.final?.grammar ?? data.ai?.grammar)}
        ${scoreCell("Overall", data.final?.overall ?? data.ai?.overall, true)}
      </div>
      <div class="report">${md(data.report)}</div>
      ${data.teacherComment ? `<blockquote>${esc(data.teacherComment)}</blockquote>` : ""}
      ${isTeacher ? `
        <h3>Score Adjustment (Teacher)</h3>
        <div class="skills-input">
          ${["taskResponse", "coherence", "lexical", "grammar"].map((k) => `
            <div><label>${k}</label><select id="ov-${k}">
            ${Array.from({ length: 17 }, (_, i) => (i + 2) / 2).map((v) =>
              `<option value="${v}" ${v === (data.final?.[k] ?? 0) ? "selected" : ""}>${v.toFixed(1)}</option>`).join("")}
            </select></div>`).join("")}
        </div>
        <input id="ov-comment" placeholder="Comment for student" style="width:100%;margin:8px 0">
        <button class="btn primary" id="ov-save">Save teacher score</button>
      ` : ""}
    ` : `<p class="muted">AI report is not ready yet.</p>`}
    <h3>Essay Text</h3>
    <div class="report" style="white-space:pre-wrap">${esc(data.text || "")}</div>`;

  el.querySelector("#ov-save") && (el.querySelector("#ov-save").onclick = async () => {
    try {
      await PUT(`/api/essays/${data.id}/override`, {
        taskResponse: parseFloat($("#ov-taskResponse").value),
        coherence: parseFloat($("#ov-coherence").value),
        lexical: parseFloat($("#ov-lexical").value),
        grammar: parseFloat($("#ov-grammar").value),
        comment: $("#ov-comment").value,
      });
      toast("Score updated");
      state.essays = await GET("/api/essays");
      renderWriting();
    } catch (e) { toast(e.message, true); }
  });
  $("#essay-editor").innerHTML = "";
  $("#essay-editor").appendChild(el);
  el.scrollIntoView({ behavior: "smooth" });
}

function scoreCell(label, v, overall = false) {
  return `<div class="score-cell ${overall ? "overall" : ""}">
    <div class="v">${fmtBand(v)}</div><div class="c">${label}</div></div>`;
}

// ---------- Speaking ----------
function renderSpeaking() {
  const el = $("#page-speaking");
  const recOK = "webkitSpeechRecognition" in window || "SpeechRecognition" in window;
  // Check for draft on boot
  const draft = localStorage.getItem("speakingDraft");
  const draftBanner = draft ? `<div class="banner" style="background:var(--warning);color:var(--bg)">⚠ Session draft saved. <button class="btn small" id="sp-resume">Resume</button></div>` : "";
  el.innerHTML = `
    ${recOK ? "" : `<div class="banner warn">⚠ Speech recognition works in Chrome/Edge. In other browsers you can type your answers.</div>`}
    ${draftBanner}
    <div class="card">
      <h2>Speaking Practice</h2>
      <div class="grid cols-2">
        ${state.speakingTopics.map((t) => {
          const cnt1 = (t.part1Questions || []).length;
          const cnt2 = t.cueCard ? 1 : 0;
          const cnt3 = (t.part3Questions || []).length;
          const canFull = cnt1 && cnt2 && cnt3;
          return `<div class="qa-item">
            <div class="question">${esc(t.title)}</div>
            <div style="display:flex;gap:6px;flex-wrap:wrap;margin-top:6px">
              <span class="badge" style="font-size:11px">Part 1: ${cnt1}</span>
              <span class="badge" style="font-size:11px;${cnt2?'':'opacity:.5'}">Part 2: ${cnt2 ? "yes" : "—"}</span>
              <span class="badge" style="font-size:11px">Part 3: ${cnt3}</span>
            </div>
            ${t.description ? `<div class="muted small" style="margin-top:4px">${esc(t.description)}</div>` : ""}
            <div style="display:flex;gap:6px;flex-wrap:wrap;margin-top:8px">
              <button class="btn small" data-topic="${t.id}" data-part="PART_1" ${cnt1 ? "" : "disabled"}>Part 1</button>
              <button class="btn small" data-topic="${t.id}" data-part="PART_2" ${cnt2 ? "" : "disabled"}>Part 2</button>
              <button class="btn small" data-topic="${t.id}" data-part="PART_3" ${cnt3 ? "" : "disabled"}>Part 3</button>
              <button class="btn small primary" data-topic="${t.id}" data-part="FULL_TEST" ${canFull ? "" : "disabled"}>Full test</button>
            </div>
          </div>`;
        }).join("") || `<div class="empty" style="grid-column:1/-1"><span class="icon">💬</span><div class="title">No topics yet</div><div class="hint">The teacher will add practice topics — check back later.</div></div>`}
      </div>
    </div>
    <div id="speaking-flow"></div>
    <div class="card">
      <h2>Session History</h2>
      <table><thead><tr><th>Date</th><th>Topic</th><th>Part</th><th>Status</th><th>Answers</th><th>Overall</th><th></th></tr></thead><tbody>
      ${state.speakingSessions.map((s) => `
        <tr><td>${s.createdAt.slice(0, 10)}</td><td>${esc(s.topic)}</td><td>${esc(s.part)}</td>
        <td>${s.status === "REPORTED" ? '<span class="badge ok">✅ report</span>' : s.status === "FINISHED" ? '<span class="badge">finished</span>' : '<span class="badge">draft</span>'}</td>
        <td>${s.answers}</td><td>${fmtBand(s.overall)}</td>
        <td><button class="btn small" data-session="${s.id}">📄 Open</button></td></tr>`).join("")
        || `<tr><td colspan="7"><div class="empty"><span class="icon">🎤</span><div class="title">No sessions yet</div><div class="hint">Select a topic above and answer questions by voice or text — AI will prepare a report.</div></div></td></tr>`}
      </tbody></table>
    </div>`;

  if (draft) {
    $("#sp-resume").onclick = () => {
      const d = JSON.parse(draft);
      const topic = state.speakingTopics.find((t) => t.id === d.topicId);
      if (!topic) { localStorage.removeItem("speakingDraft"); return renderSpeaking(); }
      state.speaking = { ...state.speaking, ...d, topic };
      renderSpeakingQuestion();
    };
  }
  el.querySelectorAll("[data-topic]").forEach((b) => b.onclick = () => startSpeakingSession(parseInt(b.dataset.topic), b.dataset.part));
  el.querySelectorAll("[data-session]").forEach((b) => b.onclick = () => viewSpeakingSession(parseInt(b.dataset.session)));
}

async function startSpeakingSession(topicId, part) {
  const topic = state.speakingTopics.find((t) => t.id === topicId);
  const sessionPart = part || "FULL_TEST";
  try {
    const res = await POST("/api/speaking/sessions", { topicId, part: sessionPart });
    state.speaking.sessionId = res.id;
    state.speaking.topic = topic;
    state.speaking.qaIndex = 0;
    state.speaking.answers = [];
    state.speaking.currentPart = null;
    state.speaking.partLabel = "";
    // Build plan
    const plan = [];
    if (sessionPart === "PART_1") {
      (topic.part1Questions || []).forEach((q) => plan.push({ part: "Part 1", label: "Part 1", q }));
    } else if (sessionPart === "PART_2") {
      if (topic.cueCard) plan.push({ part: "Part 2", label: "Part 2 — Cue Card", q: topic.cueCard });
    } else if (sessionPart === "PART_3") {
      (topic.part3Questions || []).forEach((q) => plan.push({ part: "Part 3", label: "Part 3", q }));
    } else {
      (topic.part1Questions || []).forEach((q) => plan.push({ part: "Part 1", label: "Part 1", q }));
      if (topic.cueCard) plan.push({ part: "Part 2", label: "Part 2 — Cue Card", q: topic.cueCard });
      (topic.part3Questions || []).forEach((q) => plan.push({ part: "Part 3", label: "Part 3", q }));
    }
    state.speaking.plan = plan;
    if (plan.length === 0) return toast("This part has no questions yet", true);
    renderSpeakingQuestion();
  } catch (e) { toast(e.message, true); }
}

function renderSpeakingQuestion() {
  const sp = state.speaking;
  const topic = sp.topic;
  const step = sp.plan[sp.qaIndex];
  const isLast = sp.qaIndex === sp.plan.length - 1;
  const isCue = step.label.includes("Cue Card");
  $("#speaking-flow").innerHTML = `
    <div class="card">
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px">
        <span class="badge" style="font-size:15px;padding:6px 12px;background:var(--accent);color:var(--bg);font-weight:700">${esc(step.label)}</span>
        <span class="muted small">Question ${sp.qaIndex + 1} of ${sp.plan.length}</span>
      </div>
      <h2>${esc(topic.title)}</h2>
      <div class="question ${isCue ? "cue-card" : ""}" style="font-size:17px;margin:10px 0">${esc(step.q)}</div>
      <div style="display:flex;gap:10px;align-items:center;flex-wrap:wrap">
        <button class="btn" id="sp-speak">🔊 Read question aloud</button>
        <button class="btn primary" id="sp-record"><span class="rec-indicator"><span class="rec-dot hidden" id="sp-dot"></span>🎙 Answer by voice</span></button>
        <span class="muted small">or type your answer:</span>
      </div>
      <textarea id="sp-answer" rows="${isCue ? 8 : 4}" style="width:100%;margin:10px 0" placeholder="Transcription will appear here automatically, or type your answer..."></textarea>
      <button class="btn primary" id="sp-next">${isLast ? "Finish and get report" : "Next question →"}</button>
    </div>`;

  $("#sp-speak").onclick = () => {
    const u = new SpeechSynthesisUtterance(step.q);
    u.lang = "en-US"; u.rate = 0.95;
    speechSynthesis.cancel();
    speechSynthesis.speak(u);
  };

  const answerTa = $("#sp-answer");
  const recordBtn = $("#sp-record");
  let recognizing = false;

  recordBtn.onclick = () => {
    const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SR) { answerTa.focus(); return toast("Recognition not available — type your answer", true); }
    if (recognizing) return;
    const rec = new SR();
    rec.lang = "en-US"; rec.continuous = true; rec.interimResults = true;
    let finalText = answerTa.value ? answerTa.value + " " : "";
    const startedAt = Date.now();
    recognizing = true;
    $("#sp-dot").classList.remove("hidden");
    rec.onresult = (ev) => {
      let interim = "";
      for (let i = ev.resultIndex; i < ev.results.length; i++) {
        if (ev.results[i].isFinal) finalText += ev.results[i][0].transcript + " ";
        else interim += ev.results[i][0].transcript;
      }
      answerTa.value = (finalText + interim).trim();
    };
    rec.onend = () => {
      recognizing = false;
      $("#sp-dot").classList.add("hidden");
      if (sp.answerStart) {
        sp.answers[sp.qaIndex] = sp.answers[sp.qaIndex] || {};
        sp.answers[sp.qaIndex].durationSec = Math.round((Date.now() - sp.answerStart) / 1000);
      }
    };
    rec.onerror = (ev) => {
      recognizing = false;
      $("#sp-dot").classList.add("hidden");
      if (ev.error !== "no-speech" && ev.error !== "aborted")
        toast("Recognition error: " + ev.error, true);
    };
    sp.answerStart = Date.now();
    rec.start();
    sp.recognition = rec;
  };

  $("#sp-next").onclick = () => {
    const text = answerTa.value.trim();
    if (!text) return toast("Answer is empty — speak or type an answer", true);
    if (sp.recognition) { try { sp.recognition.stop(); } catch {} }
    sp.answers[sp.qaIndex] = {
      part: step.part,
      question: step.q,
      text,
      durationSec: sp.answers[sp.qaIndex]?.durationSec
        || Math.round((Date.now() - (sp.answerStart || Date.now())) / 1000) || 5,
    };
    // Save draft
    localStorage.setItem("speakingDraft", JSON.stringify({
      sessionId: sp.sessionId, topicId: sp.topic.id, plan: sp.plan, qaIndex: sp.qaIndex + 1,
      answers: sp.answers, currentPart: sp.currentPart, partLabel: sp.partLabel,
    }));
    speechSynthesis.cancel();
    if (sp.qaIndex < sp.plan.length - 1) {
      sp.qaIndex++;
      renderSpeakingQuestion();
    } else {
      finishSpeakingSession();
    }
  };
}

async function finishSpeakingSession() {
  const sp = state.speaking;
  $("#speaking-flow").innerHTML = `<div class="card"><h2>Generating AI report...</h2><p class="muted">Usually takes 5–20 seconds.</p></div>`;
  localStorage.setItem("speakingDraft", JSON.stringify({
    sessionId: sp.sessionId, topicId: sp.topic.id, plan: sp.plan, qaIndex: sp.qaIndex,
    answers: sp.answers, currentPart: sp.currentPart, partLabel: sp.partLabel,
  }));
  try {
    const res = await POST(`/api/speaking/sessions/${sp.sessionId}/finish`, { answers: sp.answers });
    localStorage.removeItem("speakingDraft");
    state.speakingSessions = await GET("/api/speaking/sessions");
    renderSpeaking();
    if (res.reportError) {
      toast("Report failed: " + res.reportError, true);
      $("#speaking-flow").innerHTML = `<div class="card"><h2>Report generation failed</h2><p>${esc(res.reportError)}</p><button class="btn primary" id="sp-retry-report">Retry report</button></div>`;
      $("#sp-retry-report").onclick = () => finishSpeakingSession();
    } else {
      toast("Report ready");
      await viewSpeakingSession(sp.sessionId);
    }
  } catch (e) {
    toast(e.message, true);
    $("#speaking-flow").innerHTML = `<div class="card"><h2>Error</h2><p>${esc(e.message)}</p><button class="btn primary" id="sp-retry">Retry</button></div>`;
    $("#sp-retry").onclick = () => finishSpeakingSession();
  }
}

async function viewSpeakingSession(id) {
  let d;
  try { d = await GET("/api/speaking/sessions/" + id); } catch (e) { return toast(e.message, true); }
  const s = d.scores || {};
  const showRegen = d.status === "FINISHED" && (d.scores.overall === 0 || d.scores.overall == null);
  $("#speaking-flow").innerHTML = `
    <div class="card">
      <h2>${esc(d.topic)} — report (${d.part})</h2>
      ${d.scores.overall > 0 ? `
      <div class="scores">
        ${scoreCell("FC", s.fluency)}${scoreCell("LR", s.lexical)}
        ${scoreCell("GRA", s.grammar)}${scoreCell("P", s.pronunciation)}
        ${scoreCell("Overall", s.overall, true)}
      </div>` : `<p class="muted">Report has not been generated yet.</p>`}
      ${showRegen ? `<button class="btn primary" id="sp-regen-${d.id}">Generate AI report</button>` : ""}
      ${(d.qa || []).map((x, i) => `
        <div class="qa-item"><div class="question"><span class="badge small">${esc(x.part || "")}</span> Q${i + 1}. ${esc(x.question)}</div>
        <div class="answer">${esc(x.answer)}<span class="muted small"> (${x.durationSec} sec)</span></div></div>`).join("")}
      <div class="report">${md(d.report)}</div>
    </div>`;
  if (showRegen) {
    $(`#sp-regen-${d.id}`).onclick = async () => {
      try {
        await POST(`/api/speaking/sessions/${d.id}/report`);
        toast("Report generated");
        viewSpeakingSession(d.id);
      } catch (e) { toast(e.message, true); }
    };
  }
  $("#speaking-flow").scrollIntoView({ behavior: "smooth" });
}

// ---------- Teacher Cabinet ----------
function editTaskForm(id) {
  const t = state.writingTasks.find((x) => x.id === id);
  if (!t) return;
  $("#cab-task-type").value = t.taskType;
  $("#cab-task-module").value = t.moduleType;
  $("#cab-task-prompt").value = t.prompt;
  $("#cab-task-kind").value = t.promptKind || "";
  $("#cab-task-imgurl").value = t.imageUrl || "";
  $("#cab-task-img").value = "";
  const addBtn = $("#cab-task-add");
  addBtn.textContent = "Update task";
  addBtn.dataset.editId = id;
}

function editTopicForm(id) {
  const t = state.speakingTopics.find((x) => x.id === id);
  if (!t) return;
  $("#cab-sp-title").value = t.title;
  $("#cab-sp-desc").value = t.description || "";
  $("#cab-sp-p1").value = (t.part1Questions || []).join("\n");
  $("#cab-sp-cc").value = t.cueCard || "";
  $("#cab-sp-p3").value = (t.part3Questions || []).join("\n");
  const addBtn = $("#cab-sp-add");
  addBtn.textContent = "Update topic";
  addBtn.dataset.editId = id;
}

function renderCabinet() {
  const el = $("#page-cabinet");
  el.innerHTML = `
    <div class="card">
      <h2>Students</h2>
      <div class="grid cols-2" style="margin-bottom:10px">
        <input id="cab-st-name" placeholder="Full name">
        <input id="cab-st-email" placeholder="Email">
        <input id="cab-st-pass" placeholder="Password (min. 6)" type="password">
        <div style="display:flex;gap:8px">
          <input id="cab-st-target" placeholder="Target, e.g. 7.0" style="flex:1">
          <button class="btn primary" id="cab-st-add">Add</button>
        </div>
      </div>
      <table><thead><tr><th>Name</th><th>Email</th><th>Target</th><th>Groups</th><th></th></tr></thead><tbody>
      ${state.students.map((s) => `
        <tr><td>${esc(s.fullName)}</td><td>${esc(s.email)}</td><td>${s.targetBand ? s.targetBand.toFixed(1) : "—"}</td>
        <td>${(s.groups || []).join(", ")}</td>
        <td><button class="btn small danger" data-del-student="${s.id}">✕</button></td></tr>`).join("")
        || `<tr><td colspan="5" class="muted">No students yet</td></tr>`}
      </tbody></table>
    </div>
    <div class="card">
      <h2>Writing Task Bank</h2>
      <details><summary>Create new task</summary>
        <div class="grid cols-2" style="margin-bottom:10px;margin-top:10px">
          <select id="cab-task-type"><option>TASK_2</option><option>TASK_1</option></select>
          <select id="cab-task-module"><option>ACADEMIC</option><option>GENERAL</option></select>
        </div>
        <input id="cab-task-prompt" placeholder="Task text (prompt)" style="width:100%;margin-bottom:8px">
        <label style="font-size:12px;color:var(--muted)">Prompt kind (optional, e.g. "opinion", "discussion")</label>
        <input id="cab-task-kind" placeholder="Prompt kind" style="width:100%;margin-bottom:8px">
        <label style="font-size:12px;color:var(--muted)">Image (for Task 1: chart/table)</label>
        <div style="display:flex;gap:8px;margin-bottom:8px">
          <input type="file" id="cab-task-img" accept="image/png,image/jpeg,image/gif,image/webp" style="flex:1">
          <input id="cab-task-imgurl" placeholder="or image URL" style="flex:1">
        </div>
        <button class="btn primary" id="cab-task-add">Add task</button>
      </details>
      <table style="margin-top:10px"><thead><tr><th>Type</th><th>Prompt</th><th></th></tr></thead><tbody>
      ${state.writingTasks.map((t) => `
        <tr><td>${t.taskType} / ${t.moduleType}${t.promptKind ? " · " + esc(t.promptKind) : ""}${t.imageUrl ? " 🖼️" : ""}</td>
        <td style="max-width:320px">${esc(t.prompt.slice(0, 100))}${t.prompt.length > 100 ? "…" : ""}</td>
        <td><button class="btn small" data-edit-task="${t.id}">Edit</button>
            <button class="btn small danger" data-del-task="${t.id}">✕</button></td></tr>`).join("")
        || `<tr><td colspan="3" class="muted">No tasks yet</td></tr>`}
      </tbody></table>
    </div>
    <div class="card">
      <h2>Assessment Criteria</h2>
      <p class="muted small">Active set is used in the AI prompt. Upload with the same title = new version.</p>
      <input id="cab-cr-title" placeholder="Title (e.g. "IELTS Writing — Official Descriptor")" style="width:100%;margin-bottom:8px">
      <textarea id="cab-cr-content" rows="5" style="width:100%;margin-bottom:8px" placeholder="Criteria and instructions for the AI..."></textarea>
      <button class="btn primary" id="cab-cr-add">Upload / update</button>
      <table style="margin-top:12px"><thead><tr><th>Title</th><th>Version</th><th>Active</th><th></th></tr></thead><tbody>
      ${state.criteria.map((c) => `
        <tr><td>${esc(c.title)}</td><td>v${c.version}</td>
        <td>${c.active ? "✅" : ""}</td>
        <td>
          <button class="btn small" data-view-cr="${c.id}">View</button>
          <button class="btn small" data-load-cr="${c.id}">Load into editor</button>
          ${!c.active ? `<button class="btn small" data-activate-cr="${c.id}">Activate</button>` : ""}
        </td></tr>`).join("")
        || `<tr><td colspan="4" class="muted">No criteria sets yet</td></tr>`}
      </tbody></table>
    </div>
    <div class="card">
      <h2>Speaking: Topic Bank</h2>
      <details><summary>Create / edit topic</summary>
        <input id="cab-sp-title" placeholder="Topic (e.g. Environment)" style="width:100%;margin-bottom:8px;margin-top:10px">
        <textarea id="cab-sp-desc" rows="2" style="width:100%;margin-bottom:8px" placeholder="Short description"></textarea>
        <label style="font-size:12px;color:var(--muted)">Part 1 questions — one per line</label>
        <textarea id="cab-sp-p1" rows="3" style="width:100%;margin-bottom:8px" placeholder="Part 1 questions..."></textarea>
        <label style="font-size:12px;color:var(--muted)">Part 2 — Cue Card text</label>
        <textarea id="cab-sp-cc" rows="4" style="width:100%;margin-bottom:8px" placeholder="Describe ... You should say: ..."></textarea>
        <label style="font-size:12px;color:var(--muted)">Part 3 questions — one per line</label>
        <textarea id="cab-sp-p3" rows="3" style="width:100%;margin-bottom:8px" placeholder="Part 3 questions..."></textarea>
        <button class="btn primary" id="cab-sp-add">Add topic</button>
      </details>
      <table style="margin-top:10px"><thead><tr><th>Topic</th><th>P1</th><th>P2</th><th>P3</th><th></th></tr></thead><tbody>
      ${state.speakingTopics.map((t) => `
        <tr><td>${esc(t.title)}${t.description ? " · " + esc(t.description.slice(0,60)) : ""}</td>
        <td>${(t.part1Questions || []).length}</td><td>${t.cueCard ? "✅" : "—"}</td><td>${(t.part3Questions || []).length}</td>
        <td><button class="btn small" data-edit-sp="${t.id}">Edit</button>
            <button class="btn small danger" data-del-sp="${t.id}">✕</button></td></tr>`).join("")
        || `<tr><td colspan="5" class="muted">No topics yet</td></tr>`}
      </tbody></table>
    </div>`;

  $("#cab-st-add").onclick = async () => {
    const target = parseFloat($("#cab-st-target").value);
    try {
      await POST("/api/admin/students", {
        fullName: $("#cab-st-name").value, email: $("#cab-st-email").value,
        password: $("#cab-st-pass").value, targetBand: isNaN(target) ? null : target,
      });
      toast("Student created");
      state.students = await GET("/api/admin/students");
      renderCabinet();
    } catch (e) { toast(e.message, true); }
  };
  el.querySelectorAll("[data-del-student]").forEach((b) => b.onclick = async () => {
    if (!confirm("Delete student and all their data?")) return;
    try {
      await DEL("/api/admin/students/" + b.dataset.delStudent);
      state.students = await GET("/api/admin/students");
      toast("Deleted"); renderCabinet();
    } catch (e) { toast(e.message, true); }
  });
  $("#cab-task-add").onclick = async () => {
    const fileInput = $("#cab-task-img");
    let imageUrl = $("#cab-task-imgurl").value.trim() || null;
    if (fileInput.files && fileInput.files[0] && !imageUrl) {
      const fd = new FormData();
      fd.append("file", fileInput.files[0]);
      try {
        const up = await (await fetch("/api/uploads", { method: "POST", headers: { Authorization: "Bearer " + state.token }, body: fd })).json();
        imageUrl = up.url;
      } catch (e) { return toast("Image upload failed: " + e.message, true); }
    }
    const editId = $("#cab-task-add").dataset.editId;
    const payload = {
      taskType: $("#cab-task-type").value, moduleType: $("#cab-task-module").value,
      prompt: $("#cab-task-prompt").value,
      promptKind: $("#cab-task-kind").value || null,
      imageUrl: imageUrl,
    };
    try {
      if (editId) {
        await PUT("/api/writing/tasks/" + editId, payload);
        toast("Task updated");
        delete $("#cab-task-add").dataset.editId;
        $("#cab-task-add").textContent = "Add task";
      } else {
        await POST("/api/writing/tasks", payload);
        toast("Task added");
      }
      state.writingTasks = await GET("/api/writing/tasks");
      renderCabinet();
    } catch (e) { toast(e.message, true); }
  };
  el.querySelectorAll("[data-edit-task]").forEach((b) => b.onclick = () => editTaskForm(parseInt(b.dataset.editTask)));
  el.querySelectorAll("[data-del-task]").forEach((b) => b.onclick = async () => {
    if (!confirm("Delete this task?")) return;
    try { await DEL("/api/writing/tasks/" + b.dataset.delTask); state.writingTasks = await GET("/api/writing/tasks"); toast("Deleted"); renderCabinet(); } catch (e) { toast(e.message, true); }
  });
  $("#cab-cr-add").onclick = async () => {
    try {
      await POST("/api/writing/criteria", {
        title: $("#cab-cr-title").value, content: $("#cab-cr-content").value,
      });
      toast("Criteria saved");
      state.criteria = await GET("/api/writing/criteria");
      renderCabinet();
    } catch (e) { toast(e.message, true); }
  };
  el.querySelectorAll("[data-activate-cr]").forEach((b) => b.onclick = async () => {
    try {
      await PUT(`/api/writing/criteria/${b.dataset.activateCr}/activate`);
      state.criteria = await GET("/api/writing/criteria");
      renderCabinet();
    } catch (e) { toast(e.message, true); }
  });
  el.querySelectorAll("[data-view-cr]").forEach((b) => b.onclick = () => {
    const cr = state.criteria.find((c) => c.id == b.dataset.viewCr);
    if (!cr) return;
    showCriteriaModal(cr.title, cr.version, cr.content);
  });
  el.querySelectorAll("[data-load-cr]").forEach((b) => b.onclick = () => {
    const cr = state.criteria.find((c) => c.id == b.dataset.loadCr);
    if (!cr) return;
    $("#cab-cr-title").value = cr.title;
    $("#cab-cr-content").value = cr.content || "";
    toast("Loaded into editor — edit and upload to version");
  });
  $("#cab-sp-add").onclick = async () => {
    const part1 = $("#cab-sp-p1").value.split("\n").map((s) => s.trim()).filter(Boolean);
    const part3 = $("#cab-sp-p3").value.split("\n").map((s) => s.trim()).filter(Boolean);
    const payload = {
      title: $("#cab-sp-title").value,
      description: $("#cab-sp-desc").value || null,
      part1Questions: part1.length ? part1 : null,
      cueCard: $("#cab-sp-cc").value || null,
      part3Questions: part3.length ? part3 : null,
    };
    const editId = $("#cab-sp-add").dataset.editId;
    try {
      if (editId) {
        await PUT("/api/speaking/topics/" + editId, payload);
        toast("Topic updated");
        delete $("#cab-sp-add").dataset.editId;
        $("#cab-sp-add").textContent = "Add topic";
      } else {
        await POST("/api/speaking/topics", payload);
        toast("Topic added");
      }
      state.speakingTopics = await GET("/api/speaking/topics");
      renderCabinet();
    } catch (e) { toast(e.message, true); }
  };
  el.querySelectorAll("[data-edit-sp]").forEach((b) => b.onclick = () => editTopicForm(parseInt(b.dataset.editSp)));
  el.querySelectorAll("[data-del-sp]").forEach((b) => b.onclick = async () => {
    if (!confirm("Delete this topic?")) return;
    try { await DEL("/api/speaking/topics/" + b.dataset.delSp); state.speakingTopics = await GET("/api/speaking/topics"); toast("Deleted"); renderCabinet(); } catch (e) { toast(e.message, true); }
  });
}

// ---------- Initialization ----------
document.querySelectorAll(".tab").forEach((t) => t.onclick = () => {
  document.querySelectorAll(".tab").forEach((x) => x.classList.remove("active"));
  t.classList.add("active");
  $("#login-form").classList.toggle("hidden", t.dataset.tab !== "login");
  $("#register-form").classList.toggle("hidden", t.dataset.tab !== "register");
});
$("#login-email").focus();

$("#login-form").onsubmit = async (ev) => {
  ev.preventDefault();
  const btn = ev.target.querySelector("button");
  btn.classList.add("loading"); btn.disabled = true;
  try {
    const res = await POST("/api/auth/login", {
      email: $("#login-email").value, password: $("#login-password").value,
    });
    localStorage.setItem("token", res.token);
    localStorage.setItem("role", res.role);
    localStorage.setItem("fullName", res.fullName || "");
    state.token = res.token; state.role = res.role; state.fullName = res.fullName || "";
    await boot();
  } catch (e) { toast(e.message, true); }
  btn.classList.remove("loading"); btn.disabled = false;
};

$("#register-form").onsubmit = async (ev) => {
  ev.preventDefault();
  try {
    await POST("/api/auth/register", {
      fullName: $("#reg-name").value, email: $("#reg-email").value,
      password: $("#reg-password").value,
    });
    toast("Account created — now sign in");
    document.querySelector('.tab[data-tab="login"]').click();
    $("#login-email").value = $("#reg-email").value;
    $("#login-password").value = "";
  } catch (e) { toast(e.message, true); }
};

document.querySelectorAll(".nav-btn").forEach((b) => b.onclick = () => renderPage(b.dataset.page));
$("#logout-btn").onclick = logout;

boot();
