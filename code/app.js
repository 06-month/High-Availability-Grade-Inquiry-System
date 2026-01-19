// ===== Demo Data (대학교 성적조회 스타일) =====
const allRows = [
  {
    code: "COME2201",
    name: "정보보호개론",
    type: "전필",
    credit: 3,
    grade: "A+",
    point: 4.5,
    pct: 96,
    prof: "김교수",
    note: "정상",
    eval: "중간 30% / 기말 40% / 과제 20% / 출석 10%",
    desc: "보안 기본 개념 및 암호/접근통제/네트워크 보안 개요",
  },
  {
    code: "COME2302",
    name: "운영체제",
    type: "전필",
    credit: 3,
    grade: "A0",
    point: 4.0,
    pct: 90,
    prof: "이교수",
    note: "정상",
    eval: "중간 35% / 기말 35% / 과제 20% / 출석 10%",
    desc: "프로세스/스레드/메모리/파일시스템/스케줄링",
  },
  {
    code: "COME2405",
    name: "데이터베이스",
    type: "전필",
    credit: 3,
    grade: "B+",
    point: 3.5,
    pct: 86,
    prof: "박교수",
    note: "정상",
    eval: "중간 30% / 기말 40% / 과제 20% / 출석 10%",
    desc: "ERD, 정규화, SQL, 트랜잭션, 인덱스",
  },
  {
    code: "COME3101",
    name: "웹프로그래밍",
    type: "전선",
    credit: 3,
    grade: "A0",
    point: 4.0,
    pct: 92,
    prof: "최교수",
    note: "정상",
    eval: "과제 50% / 프로젝트 30% / 출석 20%",
    desc: "HTML/CSS/JS 기반 웹 개발과 프로젝트 실습",
  },
  {
    code: "COME3303",
    name: "소프트웨어공학",
    type: "전선",
    credit: 3,
    grade: "B0",
    point: 3.0,
    pct: 82,
    prof: "정교수",
    note: "정상",
    eval: "중간 30% / 기말 30% / 발표 20% / 과제 20%",
    desc: "요구사항/설계/테스트/형상관리/프로젝트 관리",
  },
  {
    code: "GENE1010",
    name: "대학글쓰기",
    type: "교필",
    credit: 2,
    grade: "A0",
    point: 4.0,
    pct: 91,
    prof: "한교수",
    note: "정상",
    eval: "과제 60% / 출석 20% / 발표 20%",
    desc: "학술적 글쓰기와 리포트 작성",
  },
  {
    code: "MATH2012",
    name: "확률과통계",
    type: "교선",
    credit: 1,
    grade: "P",
    point: null,
    pct: null,
    prof: "오교수",
    note: "Pass",
    eval: "출석 40% / 과제 60%",
    desc: "P/F 과목(평점 계산 제외)",
  },
];

let rows = [...allRows];
let sortState = { key: null, dir: 1 }; // dir: 1 asc, -1 desc

const $ = (id) => document.getElementById(id);

function getTermText(termValue) {
  if (termValue === "1") return "1학기";
  if (termValue === "2") return "2학기";
  if (termValue === "S") return "여름학기";
  return "겨울학기";
}

function formatToday() {
  const d = new Date();
  const yy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  $("today").textContent = `${yy}-${mm}-${dd}`;
}

function gradeBadgeHTML(grade) {
  if (grade === "P") return `<span class="badge B">P</span>`;
  if (grade.startsWith("A")) return `<span class="badge A">${grade}</span>`;
  if (grade.startsWith("B")) return `<span class="badge B">${grade}</span>`;
  if (grade.startsWith("C")) return `<span class="badge C">${grade}</span>`;
  return `<span class="badge D">${grade}</span>`;
}

function renderTable() {
  const tbody = $("tbody");
  tbody.innerHTML = rows
    .map((r, idx) => {
      const pointText = r.point == null ? "-" : r.point.toFixed(1);
      const pctText = r.pct == null ? "-" : r.pct;
      return `
        <tr class="clickable" data-idx="${idx}">
          <td>${r.code}</td>
          <td class="left">
            <span class="linkish">${r.name}</span>
          </td>
          <td>${r.type}</td>
          <td>${r.credit}</td>
          <td>${gradeBadgeHTML(r.grade)}</td>
          <td>${pointText}</td>
          <td>${pctText}</td>
          <td>${r.prof}</td>
          <td>${r.note}</td>
        </tr>
      `;
    })
    .join("");

  $("count").textContent = String(rows.length);

  // row click
  [...tbody.querySelectorAll("tr")].forEach((tr) => {
    tr.addEventListener("click", () => {
      const idx = Number(tr.dataset.idx);
      openDrawer(rows[idx]);
    });
  });

  renderSummary();
}

function renderSummary() {
  // 신청학점 = 전체 credit 합
  const applied = rows.reduce((acc, r) => acc + (r.credit || 0), 0);

  // 취득학점 = F 없다고 가정. (추후 F가 있으면 제외 로직 넣으면 됨)
  const earned = applied;

  // GPA 계산: P/F 제외 + (학점*평점)/학점합
  const gpaBase = rows.filter((r) => r.point != null);
  const totalCreditsForGPA = gpaBase.reduce((acc, r) => acc + (r.credit || 0), 0);
  const totalPoints = gpaBase.reduce((acc, r) => acc + (r.credit || 0) * (r.point || 0), 0);
  const gpa = totalCreditsForGPA === 0 ? 0 : totalPoints / totalCreditsForGPA;

  // 백분율 평균: pct 있는 과목만 평균
  const pctBase = rows.filter((r) => r.pct != null);
  const pctAvg =
    pctBase.length === 0
      ? 0
      : pctBase.reduce((acc, r) => acc + r.pct, 0) / pctBase.length;

  $("sumApplied").textContent = String(applied);
  $("sumEarned").textContent = String(earned);
  $("sumGPA").textContent = gpa.toFixed(2);
  $("sumPct").textContent = pctAvg.toFixed(1);

  // 학기 라벨
  const y = $("year").value;
  const t = $("term").value;
  $("termLabel").textContent = `${y}년 ${getTermText(t)}`;
}

function applyFilters() {
  const q = $("q").value.trim().toLowerCase();
  rows = allRows.filter((r) => (q ? r.name.toLowerCase().includes(q) : true));

  // 정렬 상태 유지 시 재적용
  if (sortState.key) {
    sortBy(sortState.key, true);
  } else {
    renderTable();
  }
}

function resetFilters() {
  $("year").value = "2025";
  $("term").value = "2";
  $("q").value = "";
  rows = [...allRows];
  sortState = { key: null, dir: 1 };
  renderTable();
}

function sortBy(key, keepDir = false) {
  if (!keepDir) {
    if (sortState.key === key) sortState.dir *= -1;
    else sortState = { key, dir: 1 };
  }

  const dir = sortState.dir;

  rows.sort((a, b) => {
    const av = a[key] ?? -Infinity;
    const bv = b[key] ?? -Infinity;
    // null(-Infinity)는 항상 아래로 가도록
    if (av === -Infinity && bv === -Infinity) return 0;
    if (av === -Infinity) return 1;
    if (bv === -Infinity) return -1;
    return (av - bv) * dir;
  });

  renderTable();
}

function exportCSV() {
  // 간단 CSV 다운로드 (엑셀로 열림)
  const headers = ["과목코드","과목명","이수구분","학점","등급","평점","백분율","담당교수","비고"];
  const lines = [headers.join(",")];

  rows.forEach((r) => {
    const line = [
      r.code,
      `"${r.name.replaceAll('"', '""')}"`,
      r.type,
      r.credit,
      r.grade,
      r.point == null ? "" : r.point,
      r.pct == null ? "" : r.pct,
      r.prof,
      r.note
    ].join(",");
    lines.push(line);
  });

  const blob = new Blob([lines.join("\n")], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = "grades.csv";
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

/* ===== Drawer ===== */
function openDrawer(row) {
  const y = $("year").value;
  const t = $("term").value;

  $("dTitle").textContent = row.name;
  $("dSub").textContent = `${row.code} · ${row.type} · ${row.prof}`;
  $("dTerm").textContent = `${y} / ${getTermText(t)}`;
  $("dCredit").textContent = String(row.credit);
  $("dGrade").innerHTML = gradeBadgeHTML(row.grade);
  $("dPoint").textContent = row.point == null ? "-" : row.point.toFixed(1);
  $("dPct").textContent = row.pct == null ? "-" : String(row.pct);
  $("dEval").textContent = row.eval || "-";
  $("dNote").textContent = row.note || "-";
  $("dDesc").textContent = row.desc || "-";

  $("backdrop").classList.add("show");
  $("drawer").classList.add("open");
  $("drawer").setAttribute("aria-hidden", "false");
}

function closeDrawer() {
  $("backdrop").classList.remove("show");
  $("drawer").classList.remove("open");
  $("drawer").setAttribute("aria-hidden", "true");
}

/* ===== Event Bindings ===== */
function bindEvents() {
  // 메뉴: 데모라 이동 막기
  document.querySelectorAll(".menu a").forEach((a) => {
    a.addEventListener("click", (e) => {
      e.preventDefault();
      const key = a.dataset.menu;
      if (key !== "grades") alert("데모에서는 성적조회 화면만 구현되어 있습니다.");
    });
  });

  $("logoutLink").addEventListener("click", (e) => {
    e.preventDefault();
    alert("로그아웃은 데모입니다.");
  });

  $("resetBtn").addEventListener("click", resetFilters);
  $("searchBtn").addEventListener("click", applyFilters);
  $("printBtn").addEventListener("click", () => alert("PDF 출력은 데모입니다."));
  $("excelBtn").addEventListener("click", exportCSV);

  $("sortCreditBtn").addEventListener("click", () => sortBy("credit"));
  $("sortPointBtn").addEventListener("click", () => sortBy("point"));
  $("sortPctBtn").addEventListener("click", () => sortBy("pct"));

  $("closeDrawerBtn").addEventListener("click", closeDrawer);
  $("okBtn").addEventListener("click", closeDrawer);
  $("appealBtn").addEventListener("click", () => alert("이의신청은 데모입니다."));

  $("backdrop").addEventListener("click", closeDrawer);

  // ESC로 닫기
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") closeDrawer();
  });

  // Enter로 조회
  $("q").addEventListener("keydown", (e) => {
    if (e.key === "Enter") applyFilters();
  });

  // 학년도/학기 변경 시 라벨/요약 갱신
  $("year").addEventListener("change", renderSummary);
  $("term").addEventListener("change", renderSummary);
}

function init() {
  formatToday();
  bindEvents();
  renderTable();
}

document.addEventListener("DOMContentLoaded", init);
