function sessionTemplate() {
  return document.getElementById("session-template").content
    .querySelector("*") // Find first true HTML node which will be our session markup
    .cloneNode(true);
}

export function createSession(sponsor, session, parentWrapper) {
  const element = sessionTemplate();
  getElementWithAttribute(element, "data-id").setAttribute("data-id", session["spacy.domain/id"]);
  element.querySelector("[data-slot=title]").textContent = session["spacy.domain/title"];
  element.querySelector("[data-slot=sponsor]").textContent = sponsor;
  element.querySelector("[data-slot=description]").textContent = session["spacy.domain/description"];

  if (parentWrapper) {
    const parent = document.createElement(parentWrapper);
    parent.appendChild(element);
    return parent;
  }

  return element;
}

function getElementWithAttribute(element, attributeName) {
  if (element.hasAttribute(attributeName)) {
    return element;
  }
  return element.querySelector(`[${attributeName}]`);
}

export function extractSession(element) {
  return {
    "spacy.domain/sponsor": element.querySelector("[data-slot=sponsor]")?.textContent,
    "spacy.domain/session": {
      "spacy.domain/id": getElementWithAttribute(element, "data-id")?.getAttribute("data-id"),
      "spacy.domain/title": element.querySelector("[data-slot=title]")?.textContent,
      "spacy.domain/description": element.querySelector("[data-slot=description]")?.textContent
    }
  }
}
