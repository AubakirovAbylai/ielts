// IELTS Platform SPA. Ванильный JS, токен в localStorage.
"use strict";

// ---------- Состояние ----------
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
  // Редактор эссе
  editor: { taskId: null, startedAt: null, typingStart: null, typingMs: 0, pasteCount: 0, examMode: false, timer: null },
  // Сессия speaking
  speaking: { sessionId: null, topic: null, qaIndex: 0, answers: [], recognition: null, timer: null, answerStart: null },
};

// ---------- API ----------
async function api(method, path, body) {
  const opts = { method, headers: { "Content-Type": "application/json" } };
  if (state.token) opts.headers["Authorization"] = "Bearer " + state.token;
  if (body !== undefined) opts.body = JSON.stringify(body);
  const res = await fetch(path, opts);
  const isAuthPath = path.startsWith("/api/auth/");
  // 401 на auth = неверные креды; 401 на остальном = протухший токен
  if (res.status === 401 && !isAuthPath) { logout(); throw new Error("Сессия истекла"); }
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) throw new Error((data && (data.message || data.error)) || ("HTTP " + res.status));
  return data;
}
const GET = (p) => api("GET", p);
const POST = (p, b) => api("POST", p, b);
const PUT = (p, b) => api("PUT", p, b);
const DEL = (p) => api("DELETE", p);

// ---------- Утилиты ----------
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

// Простейший markdown → HTML (заголовки, списки, **жирный**, `код`, цитаты)
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

// IELTS-округление на клиенте (для live-превью)
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
  } catch (e) { /* профиль недоступен — разлогиним при 401 внутри api() */ }
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

// ---------- Навигация ----------
const pages = ["dashboard", "tracker", "writing", "speaking", "cabinet"];
function renderPage(name) {
  pages.forEach((p) => $(`#page-${p}`).classList.toggle("hidden", p !== name));
  document.querySelectorAll(".nav-btn").forEach((b) => b.classList.toggle("active", b.dataset.page === name));
  ({ dashboard: renderDashboard, tracker: renderTracker, writing: renderWriting,
     speaking: renderSpeaking, cabinet: renderCabinet })[name]();
}

// ---------- Дашборд ----------
function renderDashboard() {
  const p = state.progress;
  const el = $("#page-dashboard");
  if (!p || p.count === 0) {
    el.innerHTML = `<div class="card hero">
      <h2>Добро пожаловать! 👋</h2>
      <p class="muted">Здесь появится ваша динамика подготовки к IELTS. Начните с первого результата!</p>
      <button class="btn" style="background:rgba(255,255,255,.92);margin-top:14px" data-goto="tracker">📈 Внести первый результат</button>
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
      <div class="card stat"><div class="value">${fmtBand(last.overall)}</div><div class="label">Последний Overall</div>
        ${delta != null ? `<div class="delta ${delta >= 0 ? "up" : "down"}">${delta >= 0 ? "▲" : "▼"} ${Math.abs(delta).toFixed(1)} к прошлому</div>` : ""}</div>
      <div class="card stat"><div class="value">${fmtBand(p.bestOverall)}</div><div class="label">Лучший Overall</div></div>
      <div class="card stat"><div class="value">${target ? target.toFixed(1) : "—"}</div><div class="label">Цель</div>
        ${gap != null ? `<div class="delta ${gap >= 0 ? "up" : "down"}">${gap >= 0 ? "цель достигнута" : "не хватает " + Math.abs(gap).toFixed(1)}</div>` : ""}</div>
      <div class="card stat"><div class="value">${p.count}</div><div class="label">Тестов внесено</div></div>
    </div>
    <div class="card"><h2>Динамика Overall</h2>${overallChart(p.series, target)}</div>
    <div class="card"><h2>Последний тест (${esc(last.date)})</h2>${skillBars(last)}</div>`;
}

function overallChart(series, target) {
  const W = 900, H = 260, pad = { l: 34, r: 10, t: 14, b: 26 };
  const iw = W - pad.l - pad.r, ih = H - pad.t - pad.b;
  const y = (v) => pad.t + ih - ((v - 4) / 5) * ih;   // шкала 4–9
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
    stroke="var(--success)" stroke-dasharray="6 4"/><text x="${W - pad.r}" y="${y(target) - 5}" text-anchor="end" font-size="11" fill="var(--success)">цель ${target}</text>`;
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

// ---------- Трекер ----------
function renderTracker() {
  const el = $("#page-tracker");
  const rows = state.progress ? state.progress.series : [];
  el.innerHTML = `
    <div class="card">
      <h2>Внести результат теста</h2>
      ${state.role === "TEACHER" ? `
      <div><label>Ученик</label>
      <select id="tr-student">
        ${(state.students || []).map((s) => `<option value="${s.id}">${esc(s.fullName || s.email)}${s.targetBand ? " · цель " + s.targetBand : ""}</option>`).join("")}
      </select></div>` : ""}
      <div class="grid cols-2">
        <div><label>Дата теста</label><input type="date" id="tr-date" value="${new Date().toISOString().slice(0, 10)}"></div>
        <div><label>Тип экзамена</label><select id="tr-type"><option>ACADEMIC</option><option>GENERAL</option></select></div>
      </div>
      <div class="skills-input">
        ${["listening", "reading", "writing", "speaking"].map((s) => `
          <div><label>${s[0].toUpperCase() + s.slice(1)}</label>
          <select id="tr-${s}"><option value="">—</option>
          ${Array.from({ length: 17 }, (_, i) => (i + 2) / 2).map((v) => `<option value="${v}">${v.toFixed(1)}</option>`).join("")}
          </select></div>`).join("")}
      </div>
      <div class="preview" id="tr-preview">Overall: — (нужно 4 навыка)</div>
      <div class="grid cols-2">
        <div><label>Источник (например, Cambridge 19, Test 2)</label><input id="tr-source" style="width:100%"></div>
        <div><label>Комментарий</label><input id="tr-comment" style="width:100%"></div>
      </div>
      <button class="btn primary" id="tr-save">💾 Сохранить результат</button>
    </div>
    <div class="card">
      <h2>История <button class="btn small" id="tr-export" style="float:right">Экспорт CSV</button></h2>
      <table><thead><tr><th>Дата</th><th>L</th><th>R</th><th>W</th><th>S</th><th>Overall</th><th>Источник</th><th></th></tr></thead>
      <tbody>${rows.slice().reverse().map((r) => `
        <tr><td>${esc(r.date)}</td><td>${fmtBand(r.listening)}</td><td>${fmtBand(r.reading)}</td>
        <td>${fmtBand(r.writing)}</td><td>${fmtBand(r.speaking)}</td><td><b>${fmtBand(r.overall)}</b></td>
        <td>${esc(r.source || "")}</td>
        <td><button class="btn small danger" data-del="${r.id}">✕</button></td></tr>`).join("")
        || `<tr><td colspan="8"><div class="empty"><span class="icon">📈</span><div class="title">История пуста</div><div class="hint">Внесите результаты пробных тестов — здесь появится динамика и график роста.</div></div></td></tr>`}
      </tbody></table>
    </div>`;

  const updatePreview = () => {
    const vals = ["listening", "reading", "writing", "speaking"].map((s) => {
      const v = $(`#tr-${s}`).value;
      return v === "" ? null : parseFloat(v);
    });
    if (vals.some((v) => v == null)) {
      $("#tr-preview").textContent = "Overall: — (нужно 4 навыка)";
    } else {
      const avg = vals.reduce((a, b) => a + b, 0) / 4;
      $("#tr-preview").innerHTML = `Среднее: <b>${avg.toFixed(3)}</b> → Overall по правилам IELTS: <b>${ieltsRound(avg).toFixed(1)}</b>`;
    }
  };
  ["listening", "reading", "writing", "speaking"].forEach((s) =>
    $(`#tr-${s}`).addEventListener("change", updatePreview));

  // Учитель: выбранный ученик (по умолчанию первый)
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
    // при первом рендере подгружаем прогресс выбранного ученика, не сбрасывая уже введённые данные:
    // перерисовываем только таблицу истории
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
    if (!body.testDate) return toast("Укажите дату", true);
    const btn = $("#tr-save");
    btn.classList.add("loading"); btn.disabled = true;
    try {
      await POST("/api/results", body);
      toast("Результат сохранён");
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
          toast("Удалено");
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
      <h2>Задания Writing</h2>
      ${state.writingTasks.length === 0 ? `<div class="empty"><span class="icon">📝</span>
        <div class="title">Заданий пока нет</div>
        <div class="hint">${isTeacher ? "Создайте первое задание в кабинете — оно появится у ваших учеников." : "Преподаватель ещё не добавил задания. Загляните позже!"}</div>
        ${isTeacher ? `<button class="btn primary" data-page="cabinet">Создать задание → Кабинет</button>` : ""}</div>` : ""}
      <table><thead><tr><th>Тип</th><th>Задание</th><th>Лимит</th><th></th></tr></thead><tbody>
      ${state.writingTasks.map((t) => `
        <tr><td>${t.taskType}<br><span class="muted small">${t.moduleType}${t.promptKind ? " · " + esc(t.promptKind) : ""}</span></td>
        <td style="max-width:420px">${esc(t.prompt.slice(0, 140))}${t.prompt.length > 140 ? "…" : ""}</td>
        <td>${t.minWords} слов<br><span class="muted small">${t.timeLimitMin} мин</span></td>
        <td><button class="btn small primary" data-write="${t.id}">Писать</button></td></tr>`).join("")}
      </tbody></table>
    </div>
    <div id="essay-editor"></div>
    <div class="card">
      <h2>Мои проверки / эссе</h2>
      <table><thead><tr><th>Дата</th><th>Ученик</th><th>Тип</th><th>Слов</th><th>Попытка</th><th>Overall</th><th>Честность</th><th></th></tr></thead><tbody>
      ${state.essays.map((e) => `
        <tr><td>${e.createdAt.slice(0, 10)}</td><td>${esc(e.studentName)}</td><td>${e.taskType}</td><td>${e.wordCount}</td>
        <td>${e.attempt}</td><td><b>${e.checked ? fmtBand(e.overall) : "—"}</b></td>
        <td>${honestyBadge(e)}</td>
        <td><button class="btn small" data-view-essay="${e.id}">📄 Открыть</button></td></tr>`).join("") || `<tr><td colspan="8"><div class="empty"><span class="icon">🗂</span><div class="title">Пока нет эссе</div><div class="hint">Выберите задание выше и напишите первое эссе — ИИ проверит его за несколько секунд.</div></div></td></tr>`}
      </tbody></table>
    </div>`;

  el.querySelectorAll("[data-write]").forEach((b) => b.onclick = () => openEditor(parseInt(b.dataset.write)));
  el.querySelectorAll("[data-view-essay]").forEach((b) => b.onclick = () => viewEssay(parseInt(b.dataset.viewEssay)));
  el.querySelectorAll('[data-page="cabinet"]').forEach((b) => b.onclick = () => renderPage("cabinet"));
}

function honestyBadge(e) {
  const flags = [];
  if (e.pasteCount > 0) flags.push(`вставок: ${e.pasteCount}`);
  if (e.typingMs > 0 && e.wordCount > 0) {
    const wpm = e.wordCount / (e.typingMs / 60000);
    if (wpm > 80) flags.push(`скорость ${wpm.toFixed(0)} слов/мин ⚠`);
  }
  return flags.length ? `<span class="small" style="color:var(--warning)">${flags.join(", ")}</span>`
    : `<span class="small" style="color:var(--success)">ок${e.examMode ? " · экзамен-режим" : ""}</span>`;
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
        <button class="btn small" id="ed-back">← К заданиям</button>
        <h2 style="margin:0;flex:1">Новое эссе — ${task.taskType} (${task.moduleType})</h2>
      </div>
      <div class="banner" style="background:var(--bg);margin:12px 0">${esc(task.prompt)}</div>
      <div class="essay-stats">
        <label style="margin:0"><input type="checkbox" id="ed-exam"> Экзаменационный режим (${task.timeLimitMin} мин, таймер)</label>
        <span class="timer" id="ed-timer">${task.timeLimitMin}:00</span>
      </div>
      <textarea id="essay-textarea" placeholder="Пишите эссе здесь..."></textarea>
      <div class="essay-stats">
        <span id="ed-words">0 слов</span>
        <span class="muted small" id="ed-min">минимум ${task.minWords}</span>
        <span style="flex:1"></span>
        <button class="btn primary" id="ed-submit">✓ Отправить на проверку ИИ</button>
      </div>
    </div>`;
  $("#ed-back").onclick = () => { clearInterval(ed.timer); $("#essay-editor").innerHTML = ""; };

  const ta = $("#essay-textarea");
  const updateWords = () => {
    const n = ta.value.trim() ? ta.value.trim().split(/\s+/).length : 0;
    $("#ed-words").innerHTML = `<span class="${n >= task.minWords ? "word-ok" : "word-low"}">${n} слов</span>`;
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
        if (left-- <= 0) { clearInterval(ed.timer); toast("Время вышло! Сдавайте эссе.", true); }
      };
      tick();
      ed.timer = setInterval(tick, 1000);
    } else {
      $("#ed-timer").textContent = task.timeLimitMin + ":00";
    }
  };

  $("#ed-submit").onclick = async () => {
    const text = ta.value.trim();
    if (!text) return toast("Эссе пустое", true);
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
      if (res.belowMinWords) toast(`Внимание: ${res.wordCount} слов — ниже лимита ${task.minWords}`, true);
      if (res.checkError) {
        toast("Эссе сохранено, но ИИ-проверка не удалась: " + res.checkError, true);
      } else {
        toast("Проверка готова");
        viewEssay(res.essayId);
      }
      renderWriting();
    } catch (e) {
      toast(e.message, true);
      btn.classList.remove("loading"); btn.disabled = false; btn.textContent = "✓ Отправить на проверку ИИ";
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
    <h2>Эссе #${data.id} — ${data.taskType}, попытка ${data.attempt}</h2>
    <p class="muted small">${esc(data.studentName)} · ${data.wordCount} слов · ${honestyBadge(data)}</p>
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
        <h3>Корректировка оценки (учитель)</h3>
        <div class="skills-input">
          ${["taskResponse", "coherence", "lexical", "grammar"].map((k) => `
            <div><label>${k}</label><select id="ov-${k}">
            ${Array.from({ length: 17 }, (_, i) => (i + 2) / 2).map((v) =>
              `<option value="${v}" ${v === (data.final?.[k] ?? 0) ? "selected" : ""}>${v.toFixed(1)}</option>`).join("")}
            </select></div>`).join("")}
        </div>
        <input id="ov-comment" placeholder="Комментарий ученику" style="width:100%;margin:8px 0">
        <button class="btn primary" id="ov-save">Сохранить оценку учителя</button>
      ` : ""}
    ` : `<p class="muted">Отчёт ИИ ещё не готов.</p>`}
    <h3>Текст эссе</h3>
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
      toast("Оценка обновлена");
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
  el.innerHTML = `
    ${recOK ? "" : `<div class="banner warn">⚠ Распознавание речи работает в Chrome/Edge. В других браузерах ответы можно вводить текстом.</div>`}
    <div class="card">
      <h2>Практика Speaking</h2>
      <div class="grid cols-2">
        ${state.speakingTopics.map((t) => `
          <div class="qa-item">
            <div class="question">${esc(t.title)} <span class="muted small">(${t.questions.length} вопросов)</span></div>
            <div class="muted small">${esc(t.description || "")}</div>
            <button class="btn small primary" data-topic="${t.id}" style="margin-top:8px">🎙 Начать сессию</button>
          </div>`).join("") || `<div class="empty" style="grid-column:1/-1"><span class="icon">💬</span><div class="title">Тем пока нет</div><div class="hint">Преподаватель добавит темы для практики — возвращайтесь позже.</div></div>`}
      </div>
    </div>
    <div id="speaking-flow"></div>
    <div class="card">
      <h2>История сессий</h2>
      <table><thead><tr><th>Дата</th><th>Тема</th><th>Статус</th><th>Ответов</th><th>Overall</th><th></th></tr></thead><tbody>
      ${state.speakingSessions.map((s) => `
        <tr><td>${s.createdAt.slice(0, 10)}</td><td>${esc(s.topic)}</td>
        <td>${s.status === "REPORTED" ? '<span class="badge ok">✅ отчёт</span>' : s.status === "FINISHED" ? '<span class="badge">завершена</span>' : '<span class="badge">черновик</span>'}</td>
        <td>${s.answers}</td><td>${fmtBand(s.overall)}</td>
        <td><button class="btn small" data-session="${s.id}">📄 Открыть</button></td></tr>`).join("")
        || `<tr><td colspan="6"><div class="empty"><span class="icon">🎤</span><div class="title">Сессий пока нет</div><div class="hint">Выберите тему выше и ответьте на вопросы голосом или текстом — ИИ подготовит отчёт.</div></div></td></tr>`}
      </tbody></table>
    </div>`;

  el.querySelectorAll("[data-topic]").forEach((b) => b.onclick = () => startSpeakingSession(parseInt(b.dataset.topic)));
  el.querySelectorAll("[data-session]").forEach((b) => b.onclick = () => viewSpeakingSession(parseInt(b.dataset.session)));
}

async function startSpeakingSession(topicId) {
  const topic = state.speakingTopics.find((t) => t.id === topicId);
  try {
    const res = await POST("/api/speaking/sessions", { topicId, part: "TOPIC_PRACTICE" });
    state.speaking.sessionId = res.id;
    state.speaking.topic = topic;
    state.speaking.qaIndex = 0;
    state.speaking.answers = [];
    renderSpeakingQuestion();
  } catch (e) { toast(e.message, true); }
}

function renderSpeakingQuestion() {
  const sp = state.speaking;
  const topic = sp.topic;
  const q = topic.questions[sp.qaIndex];
  const isLast = sp.qaIndex === topic.questions.length - 1;
  $("#speaking-flow").innerHTML = `
    <div class="card">
      <h2>${esc(topic.title)} — вопрос ${sp.qaIndex + 1} из ${topic.questions.length}</h2>
      <div class="question" style="font-size:17px;margin:10px 0">${esc(q)}</div>
      <div style="display:flex;gap:10px;align-items:center;flex-wrap:wrap">
        <button class="btn" id="sp-speak">🔊 Озвучить вопрос</button>
        <button class="btn primary" id="sp-record"><span class="rec-indicator"><span class="rec-dot hidden" id="sp-dot"></span>🎙 Ответить голосом</span></button>
        <span class="muted small">или введите ответ текстом:</span>
      </div>
      <textarea id="sp-answer" rows="4" style="width:100%;margin:10px 0" placeholder="Транскрипция появится здесь автоматически, либо напишите ответ..."></textarea>
      <button class="btn primary" id="sp-next">${isLast ? "Завершить и получить отчёт" : "Следующий вопрос →"}</button>
    </div>`;

  $("#sp-speak").onclick = () => {
    const u = new SpeechSynthesisUtterance(q);
    u.lang = "en-US"; u.rate = 0.95;
    speechSynthesis.cancel();
    speechSynthesis.speak(u);
  };

  const answerTa = $("#sp-answer");
  const recordBtn = $("#sp-record");
  let recognizing = false;

  recordBtn.onclick = () => {
    const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SR) { answerTa.focus(); return toast("Распознавание недоступно — введите ответ текстом", true); }
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
        toast("Ошибка распознавания: " + ev.error, true);
    };
    sp.answerStart = Date.now();
    rec.start();
    sp.recognition = rec;
  };

  $("#sp-next").onclick = () => {
    const text = answerTa.value.trim();
    if (!text) return toast("Ответ пуст — поговорите или напишите ответ", true);
    if (sp.recognition) { try { sp.recognition.stop(); } catch {} }
    sp.answers[sp.qaIndex] = {
      question: topic.questions[sp.qaIndex],
      text,
      durationSec: sp.answers[sp.qaIndex]?.durationSec
        || Math.round((Date.now() - (sp.answerStart || Date.now())) / 1000) || 5,
    };
    speechSynthesis.cancel();
    if (sp.qaIndex < topic.questions.length - 1) {
      sp.qaIndex++;
      renderSpeakingQuestion();
    } else {
      finishSpeakingSession();
    }
  };
}

async function finishSpeakingSession() {
  const sp = state.speaking;
  $("#speaking-flow").innerHTML = `<div class="card"><h2>Генерируем отчёт ИИ…</h2><p class="muted">Обычно занимает 5–20 секунд.</p></div>`;
  try {
    const res = await POST(`/api/speaking/sessions/${sp.sessionId}/finish`, { answers: sp.answers });
    state.speakingSessions = await GET("/api/speaking/sessions");
    renderSpeaking();
    if (res.reportError) {
      toast("Отчёт не удался: " + res.reportError, true);
      $("#speaking-flow").innerHTML = "";
    } else {
      toast("Отчёт готов");
      await viewSpeakingSession(sp.sessionId);
    }
  } catch (e) {
    toast(e.message, true);
    $("#speaking-flow").innerHTML = "";
  }
}

async function viewSpeakingSession(id) {
  let d;
  try { d = await GET("/api/speaking/sessions/" + id); } catch (e) { return toast(e.message, true); }
  const s = d.scores || {};
  $("#speaking-flow").innerHTML = `
    <div class="card">
      <h2>${esc(d.topic)} — отчёт (${d.part})</h2>
      ${d.scores.overall > 0 ? `
      <div class="scores">
        ${scoreCell("FC", s.fluency)}${scoreCell("LR", s.lexical)}
        ${scoreCell("GRA", s.grammar)}${scoreCell("P", s.pronunciation)}
        ${scoreCell("Overall", s.overall, true)}
      </div>` : `<p class="muted">Отчёт ещё не сгенерирован.</p>`}
      ${(d.qa || []).map((x, i) => `
        <div class="qa-item"><div class="question">Q${i + 1}. ${esc(x.question)}</div>
        <div class="answer">${esc(x.answer)}<span class="muted small"> (${x.durationSec} сек)</span></div></div>`).join("")}
      <div class="report">${md(d.report)}</div>
    </div>`;
  $("#speaking-flow").scrollIntoView({ behavior: "smooth" });
}

// ---------- Кабинет учителя ----------
function renderCabinet() {
  const el = $("#page-cabinet");
  el.innerHTML = `
    <div class="card">
      <h2>Ученики</h2>
      <div class="grid cols-2" style="margin-bottom:10px">
        <input id="cab-st-name" placeholder="Имя и фамилия">
        <input id="cab-st-email" placeholder="Email">
        <input id="cab-st-pass" placeholder="Пароль (мин. 6)" type="password">
        <div style="display:flex;gap:8px">
          <input id="cab-st-target" placeholder="Цель, напр. 7.0" style="flex:1">
          <button class="btn primary" id="cab-st-add">Добавить</button>
        </div>
      </div>
      <table><thead><tr><th>Имя</th><th>Email</th><th>Цель</th><th>Группы</th><th></th></tr></thead><tbody>
      ${state.students.map((s) => `
        <tr><td>${esc(s.fullName)}</td><td>${esc(s.email)}</td><td>${s.targetBand ? s.targetBand.toFixed(1) : "—"}</td>
        <td>${(s.groups || []).join(", ")}</td>
        <td><button class="btn small danger" data-del-student="${s.id}">✕</button></td></tr>`).join("")
        || `<tr><td colspan="5" class="muted">Учеников пока нет</td></tr>`}
      </tbody></table>
    </div>
    <div class="card">
      <h2>Банк заданий Writing</h2>
      <div class="grid cols-2" style="margin-bottom:10px">
        <select id="cab-task-type"><option>TASK_2</option><option>TASK_1</option></select>
        <select id="cab-task-module"><option>ACADEMIC</option><option>GENERAL</option></select>
      </div>
      <input id="cab-task-prompt" placeholder="Текст задания (prompt)" style="width:100%;margin-bottom:8px">
      <button class="btn primary" id="cab-task-add">Добавить задание</button>
    </div>
    <div class="card">
      <h2>Критерии проверки</h2>
      <p class="muted small">Активный набор используется в промпте ИИ. Новая загрузка с тем же названием = новая версия.</p>
      <input id="cab-cr-title" placeholder="Название (напр. «IELTS Writing — официальный дескриптор»)" style="width:100%;margin-bottom:8px">
      <textarea id="cab-cr-content" rows="5" style="width:100%;margin-bottom:8px" placeholder="Критерии и методические указания для ИИ..."></textarea>
      <button class="btn primary" id="cab-cr-add">Загрузить / обновить</button>
      <table style="margin-top:12px"><thead><tr><th>Название</th><th>Версия</th><th>Активен</th><th></th></tr></thead><tbody>
      ${state.criteria.map((c) => `
        <tr><td>${esc(c.title)}</td><td>v${c.version}</td>
        <td>${c.active ? "✅" : ""}</td>
        <td>${!c.active ? `<button class="btn small" data-activate-cr="${c.id}">Активировать</button>` : ""}</td></tr>`).join("")
        || `<tr><td colspan="4" class="muted">Критериев пока нет</td></tr>`}
      </tbody></table>
    </div>
    <div class="card">
      <h2>Speaking: банк тем</h2>
      <input id="cab-sp-title" placeholder="Тема (напр. Environment)" style="width:100%;margin-bottom:8px">
      <textarea id="cab-sp-questions" rows="4" style="width:100%;margin-bottom:8px" placeholder="Вопросы — каждый с новой строки"></textarea>
      <button class="btn primary" id="cab-sp-add">Добавить тему</button>
    </div>`;

  $("#cab-st-add").onclick = async () => {
    const target = parseFloat($("#cab-st-target").value);
    try {
      await POST("/api/admin/students", {
        fullName: $("#cab-st-name").value, email: $("#cab-st-email").value,
        password: $("#cab-st-pass").value, targetBand: isNaN(target) ? null : target,
      });
      toast("Ученик создан");
      state.students = await GET("/api/admin/students");
      renderCabinet();
    } catch (e) { toast(e.message, true); }
  };
  el.querySelectorAll("[data-del-student]").forEach((b) => b.onclick = async () => {
    if (!confirm("Удалить ученика и все его данные?")) return;
    try {
      await DEL("/api/admin/students/" + b.dataset.delStudent);
      state.students = await GET("/api/admin/students");
      toast("Удалён"); renderCabinet();
    } catch (e) { toast(e.message, true); }
  });
  $("#cab-task-add").onclick = async () => {
    try {
      await POST("/api/writing/tasks", {
        taskType: $("#cab-task-type").value, moduleType: $("#cab-task-module").value,
        prompt: $("#cab-task-prompt").value,
      });
      toast("Задание добавлено");
      state.writingTasks = await GET("/api/writing/tasks");
      renderCabinet();
    } catch (e) { toast(e.message, true); }
  };
  $("#cab-cr-add").onclick = async () => {
    try {
      await POST("/api/writing/criteria", {
        title: $("#cab-cr-title").value, content: $("#cab-cr-content").value,
      });
      toast("Критерии сохранены");
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
  $("#cab-sp-add").onclick = async () => {
    const questions = $("#cab-sp-questions").value.split("\n").map((s) => s.trim()).filter(Boolean);
    try {
      await POST("/api/speaking/topics", { title: $("#cab-sp-title").value, questions });
      toast("Тема добавлена");
      state.speakingTopics = await GET("/api/speaking/topics");
      renderCabinet();
    } catch (e) { toast(e.message, true); }
  };
}

// ---------- Инициализация ----------
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
    toast("Аккаунт создан — теперь войдите");
    document.querySelector('.tab[data-tab="login"]').click();
    $("#login-email").value = $("#reg-email").value;
    $("#login-password").value = "";
  } catch (e) { toast(e.message, true); }
};

document.querySelectorAll(".nav-btn").forEach((b) => b.onclick = () => renderPage(b.dataset.page));
$("#logout-btn").onclick = logout;

boot();
