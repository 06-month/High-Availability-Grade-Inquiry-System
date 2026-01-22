// ✅ 로그인 체크(로그인 안 했으면 login으로)
(function guard() {
  if (localStorage.getItem("isLoggedIn") !== "true") {
    window.location.href = "../login/index.html";
    return;
  }
})();

function doLogout() {
  localStorage.removeItem("isLoggedIn");
  localStorage.removeItem("userId");
  window.location.href = "../login/index.html";
}

// ===== API 호출 함수들 =====
async function fetchGradeSummary(studentId, semester) {
  try {
    const response = await fetch(`/api/grades/summary?studentId=${studentId}&semester=${semester}`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        // 실제 환경에서는 JWT 토큰 추가
        // 'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    });

    if (!response.ok) {
      if (response.status === 403) {
        throw new Error('성적 공개 기간이 아닙니다.');
      }
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  } catch (error) {
    console.error('Grade summary fetch failed:', error);
    throw error;
  }
}

async function fetchGradeList(studentId, semester) {
  try {
    const response = await fetch(`/api/grades/list?studentId=${studentId}&semester=${semester}`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        // 실제 환경에서는 JWT 토큰 추가
        // 'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    });

    if (!response.ok) {
      if (response.status === 403) {
        throw new Error('성적 공개 기간이 아닙니다.');
      }
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  } catch (error) {
    console.error('Grade list fetch failed:', error);
    throw error;
  }
}

let rows = [];
let sortState = { key: null, dir: 1 };
let currentRow = null;

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
  tbody.innerHTML = rows.map((r, idx) => {
    const pointText = r.point == null ? "-" : r.point.toFixed(1);
    const pctText = r.pct == null ? "-" : r.pct;
    return `
      <tr class="clickable" data-idx="${idx}">
        <td>${r.code}</td>
        <td class="left"><span class="linkish">${r.name}</span></td>
        <td>${r.type}</td>
        <td>${r.credit}</td>
        <td>${gradeBadgeHTML(r.grade)}</td>
        <td>${pointText}</td>
        <td>${pctText}</td>
        <td>${r.prof}</td>
        <td>${r.note}</td>
      </tr>
    `;
  }).join("");

  $("count").textContent = String(rows.length);

  [...tbody.querySelectorAll("tr")].forEach((tr) => {
    tr.addEventListener("click", () => {
      const idx = Number(tr.dataset.idx);
      openDrawer(rows[idx]);
    });
  });

  renderSummary();
}

async function renderSummary() {
  const y = $("year").value;
  const t = $("term").value;
  const semester = `${y}-${t}`;
  const studentId = localStorage.getItem("userId") || "12345";

  try {
    // API에서 요약 정보 가져오기
    const summaryData = await fetchGradeSummary(studentId, semester);

    $("sumApplied").textContent = String(summaryData.totalCredits);
    $("sumEarned").textContent = String(summaryData.totalCredits);
    $("sumGPA").textContent = summaryData.gpa.toFixed(2);

    // 백분율은 GPA에서 계산 (4.5 만점 기준)
    const pctFromGPA = (summaryData.gpa / 4.5) * 100;
    $("sumPct").textContent = pctFromGPA.toFixed(1);

    $("termLabel").textContent = `${y}년 ${getTermText(t)}`;
  } catch (error) {
    console.error('Failed to load grade summary:', error);
    showError(error.message);

    // 에러 시 기본값 표시
    $("sumApplied").textContent = "0";
    $("sumEarned").textContent = "0";
    $("sumGPA").textContent = "0.00";
    $("sumPct").textContent = "0.0";
    $("termLabel").textContent = `${y}년 ${getTermText(t)}`;
  }
}

async function applyFilters() {
  const y = $("year").value;
  const t = $("term").value;
  const semester = `${y}-${t}`;
  const q = $("q").value.trim().toLowerCase();
  const studentId = localStorage.getItem("userId") || "12345";

  try {
    showLoading(true);

    // API에서 성적 목록 가져오기
    const apiData = await fetchGradeList(studentId, semester);

    // API 데이터를 클라이언트 형식으로 변환
    rows = apiData.map(item => ({
      code: item.courseCode,
      name: item.courseName,
      type: determineSubjectType(item.courseCode), // 과목코드로 이수구분 판단
      credit: item.credit,
      grade: item.gradeLetter,
      point: calculateGradePoint(item.gradeLetter), // 등급으로 평점 계산
      pct: item.score,
      prof: "교수", // API에서 제공되지 않으면 기본값
      note: item.isFinalized ? "정상" : "미확정",
      eval: "중간 30% / 기말 40% / 과제 20% / 출석 10%", // 기본값
      desc: `${item.courseName} 과목` // 기본값
    }));

    // 검색 필터 적용
    if (q) {
      rows = rows.filter((r) => r.name.toLowerCase().includes(q));
    }

    if (sortState.key) sortBy(sortState.key, true);
    else renderTable();

  } catch (error) {
    console.error('Failed to load grade list:', error);
    showError(error.message);
    rows = [];
    renderTable();
  } finally {
    showLoading(false);
  }
}

function determineSubjectType(courseCode) {
  if (courseCode.startsWith("GENE")) return "교필";
  if (courseCode.startsWith("COME")) return "전필";
  if (courseCode.startsWith("MATH")) return "교선";
  return "전선";
}

function calculateGradePoint(gradeLetter) {
  const gradeMap = {
    "A+": 4.5, "A0": 4.0, "A-": 3.7,
    "B+": 3.3, "B0": 3.0, "B-": 2.7,
    "C+": 2.3, "C0": 2.0, "C-": 1.7,
    "D+": 1.3, "D0": 1.0, "D-": 0.7,
    "F": 0.0, "P": null, "NP": null
  };
  return gradeMap[gradeLetter] || null;
}

function showLoading(show) {
  const loadingEl = $("loading");
  if (loadingEl) {
    loadingEl.style.display = show ? "block" : "none";
  }
}

function showError(message) {
  alert(`오류: ${message}`);
}

function resetFilters() {
  $("year").value = "2024";
  $("term").value = "1";
  $("q").value = "";
  rows = [];
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
    if (av === -Infinity && bv === -Infinity) return 0;
    if (av === -Infinity) return 1;
    if (bv === -Infinity) return -1;
    return (av - bv) * dir;
  });

  renderTable();
}

function exportCSV() {
  if (rows.length === 0) {
    alert("내보낼 데이터가 없습니다.");
    return;
  }

  const headers = ["과목코드", "과목명", "이수구분", "학점", "등급", "평점", "백분율", "담당교수", "비고"];
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

/* Drawer */
function openDrawer(row) {
  closeAppealModal();
  currentRow = row;

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
  closeAppealModal();
  $("backdrop").classList.remove("show");
  $("drawer").classList.remove("open");
  $("drawer").setAttribute("aria-hidden", "true");
  currentRow = null;
}

/* Appeal modal */
function openAppealModal() {
  if (!currentRow) return;

  $("appealSub").textContent = `${currentRow.name} (${currentRow.code}) · ${currentRow.type} · ${currentRow.prof}`;
  $("appealText").value = "";

  $("appealBackdrop").classList.add("show");
  $("appealModal").classList.add("open");
  $("appealModal").setAttribute("aria-hidden", "false");

  setTimeout(() => $("appealText").focus(), 0);
}

function closeAppealModal() {
  $("appealBackdrop").classList.remove("show");
  $("appealModal").classList.remove("open");
  $("appealModal").setAttribute("aria-hidden", "true");
}

/* Events */
function bindEvents() {
  // 로그아웃
  const logoutLink = $("logoutLink");
  logoutLink.addEventListener("click", (e) => {
    e.preventDefault();
    doLogout();
  });

  // 상단 사용자 표시: 학번/사번
  const userId = localStorage.getItem("userId");
  if (userId) $("userName").textContent = userId;

  $("resetBtn").addEventListener("click", resetFilters);
  $("searchBtn").addEventListener("click", applyFilters);
  $("printBtn").addEventListener("click", () => alert("PDF 출력은 데모입니다."));
  $("excelBtn").addEventListener("click", exportCSV);

  $("sortCreditBtn").addEventListener("click", () => sortBy("credit"));
  $("sortPointBtn").addEventListener("click", () => sortBy("point"));
  $("sortPctBtn").addEventListener("click", () => sortBy("pct"));

  $("closeDrawerBtn").addEventListener("click", closeDrawer);
  $("okBtn").addEventListener("click", closeDrawer);
  $("backdrop").addEventListener("click", closeDrawer);

  $("appealBtn").addEventListener("click", () => {
    if (!currentRow) {
      alert("먼저 과목을 선택해주세요.");
      return;
    }
    openAppealModal();
  });

  $("closeAppealBtn").addEventListener("click", closeAppealModal);
  $("cancelAppealBtn").addEventListener("click", closeAppealModal);
  $("appealBackdrop").addEventListener("click", closeAppealModal);

  $("submitAppealBtn").addEventListener("click", () => {
    const text = $("appealText").value.trim();
    if (!text) {
      alert("이의신청 내용을 입력해주세요.");
      $("appealText").focus();
      return;
    }
    alert(`이의신청이 제출되었습니다(데모).\n\n과목: ${currentRow.name} (${currentRow.code})\n내용: ${text}`);
    closeAppealModal();
  });

  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") {
      closeAppealModal();
      closeDrawer();
    }
  });

  $("q").addEventListener("keydown", (e) => {
    if (e.key === "Enter") applyFilters();
  });

  $("year").addEventListener("change", renderSummary);
  $("term").addEventListener("change", renderSummary);
}

function init() {
  formatToday();
  bindEvents();

  // 초기 데이터 로드
  applyFilters();

  // 시작 상태 확정
  closeAppealModal();
}

document.addEventListener("DOMContentLoaded", init);
