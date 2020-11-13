function sessionTemplate() {
  return document.getElementById("session-template").content
    .querySelector("*") // Find first true HTML node which will be our session markup
    .cloneNode(true);
}

export function createSession(sponsor, session) {
  const element = sessionTemplate();
  element.querySelector("[id]").id = session.id;
  element.querySelector("[data-slot=title]").textContent = session.title;
  element.querySelector("[data-slot=sponsor]").textContent = sponsor;
  element.querySelector("[data-slot=description]").textContent = session.description;

  return element;
}
