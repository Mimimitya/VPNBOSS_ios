const states = [
  {
    theme: "light",
    heading: "С КОТОРЫМ ВСЕ",
    word: "ПРОСТО",
    description:
      "Собственный, удобный клиент или<br />возможность использовать сторонние<br />клиенты (V2RayTun, HAPP, V2Box, и тд.)<br />Настройка в 2 клика",
    width: "430px",
    font: "72px",
    rotation: "0deg",
  },
  {
    theme: "dark",
    heading: "С КОТОРЫМ ВСЕ",
    word: "ПРИВАТНО",
    description:
      "Ваш траффик:<br />● Шифруются<br />● Не продается<br />● Не сохраняется и не просматривается",
    width: "520px",
    font: "64px",
    rotation: "7deg",
  },
  {
    theme: "light",
    heading: "С КОТОРЫМ ТЫ",
    word: "ЭКОНОМИШЬ",
    description: "Подписка от 25₽/Месяц. Пробный<br />период на 5 дней.",
    width: "560px",
    font: "58px",
    rotation: "-7deg",
  },
];

const page = document.querySelector(".page");
const headingLine = document.querySelector("#headingLine");
const valuePill = document.querySelector("#valuePill");
const valueWord = document.querySelector("#valueWord");
const description = document.querySelector("#description");
const dots = [...document.querySelectorAll(".dot")];

let current = 0;
let locked = false;
let touchStartY = 0;

function setState(nextIndex) {
  if (nextIndex === current || locked) return;

  locked = true;
  current = (nextIndex + states.length) % states.length;
  const state = states[current];

  valueWord.classList.add("is-changing");
  description.classList.add("is-changing");

  window.setTimeout(() => {
    page.dataset.theme = state.theme;
    page.dataset.state = String(current);
    headingLine.textContent = state.heading;
    valueWord.textContent = state.word;
    description.innerHTML = state.description;
    valuePill.style.setProperty("--pill-width", state.width);
    valuePill.style.setProperty("--pill-font", state.font);
    valuePill.style.setProperty("--pill-rotation", state.rotation);

    dots.forEach((dot, index) => {
      dot.classList.toggle("active", index === current);
    });

    valueWord.classList.remove("is-changing");
    description.classList.remove("is-changing");
  }, 210);

  window.setTimeout(() => {
    locked = false;
  }, 900);
}

function advance(direction) {
  setState(current + direction);
}

window.addEventListener(
  "wheel",
  (event) => {
    event.preventDefault();
    if (Math.abs(event.deltaY) < 12) return;
    advance(event.deltaY > 0 ? 1 : -1);
  },
  { passive: false },
);

window.addEventListener("keydown", (event) => {
  if (["ArrowDown", "PageDown", " "].includes(event.key)) {
    event.preventDefault();
    advance(1);
  }

  if (["ArrowUp", "PageUp"].includes(event.key)) {
    event.preventDefault();
    advance(-1);
  }
});

window.addEventListener(
  "touchstart",
  (event) => {
    touchStartY = event.touches[0].clientY;
  },
  { passive: true },
);

window.addEventListener(
  "touchmove",
  (event) => {
    const diff = touchStartY - event.touches[0].clientY;
    if (Math.abs(diff) < 42) return;
    event.preventDefault();
    advance(diff > 0 ? 1 : -1);
    touchStartY = event.touches[0].clientY;
  },
  { passive: false },
);

dots.forEach((dot) => {
  dot.addEventListener("click", () => {
    setState(Number(dot.dataset.index));
  });
});
