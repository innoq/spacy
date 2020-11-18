function sessionTemplate() {
  return document.getElementById("session-template").content
    .querySelector("*") // Find first true HTML node which will be our session markup
    .cloneNode(true);
}

export function createSession(sponsor, session) {
  const element = sessionTemplate();
  element.querySelector("[data-id]").setAttribute("data-id", session["spacy.domain/id"]);
  element.querySelector("[data-slot=title]").textContent = session["spacy.domain/title"];
  element.querySelector("[data-slot=sponsor]").textContent = sponsor;
  element.querySelector("[data-slot=description]").textContent = session["spacy.domain/description"];

  return element;
}
