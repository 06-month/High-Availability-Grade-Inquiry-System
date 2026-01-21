const $ = (id) => document.getElementById(id);

function setMsg(text, type = "info") {
  const el = $("msg");
  el.textContent = text;

  if (type === "error") el.style.color = "#b91c1c";
  else if (type === "success") el.style.color = "#166534";
  else el.style.color = "#1f4e8c";
}

function isEmpty(v) {
  return !v || !String(v).trim();
}

function redirectIfAlreadyLoggedIn() {
  if (localStorage.getItem("isLoggedIn") === "true") {
    window.location.href = "../main/index.html";
  }
}

function init() {
  redirectIfAlreadyLoggedIn();

  const form = $("loginForm");
  const userId = $("userId");
  const password = $("password");

  form.addEventListener("submit", (e) => {
    e.preventDefault();

    const idVal = userId.value.trim();
    const pwVal = password.value;

    if (isEmpty(idVal)) {
      setMsg("학번/사번을 입력해주세요.", "error");
      userId.focus();
      return;
    }
    if (isEmpty(pwVal)) {
      setMsg("비밀번호를 입력해주세요.", "error");
      password.focus();
      return;
    }
    if (idVal.length < 4) {
      setMsg("학번/사번을 다시 확인해주세요(너무 짧습니다).", "error");
      userId.focus();
      return;
    }

    // ✅ 데모 로그인 성공
    localStorage.setItem("isLoggedIn", "true");
    localStorage.setItem("userId", idVal);

    setMsg("로그인 성공! 메인 화면으로 이동합니다...", "success");
    window.location.href = "../main/index.html";
  });

  password.addEventListener("keyup", (e) => {
    if (e.getModifierState && e.getModifierState("CapsLock")) {
      setMsg("Caps Lock이 켜져 있습니다.", "error");
    } else if ($("msg").textContent.includes("Caps Lock")) {
      setMsg("");
    }
  });
}

document.addEventListener("DOMContentLoaded", init);
