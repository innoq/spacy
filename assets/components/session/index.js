import { removeNode } from "uitil/dom";

function sessionTemplate() {
  return document.getElementById("session-template").content
    .querySelector("*") // Find first true HTML node which will be our session markup
    .cloneNode(true);
}

export function currentUser() {
  return document.querySelector("[current-user]").getAttribute("current-user");
}

export function createSession(sponsor, session, { parentWrapper, actions = ["delete-session"] }) {
  const element = sessionTemplate();
  const id = session["spacy.domain/id"];
  element.setAttribute("data-id", id);
  element.querySelector("[data-slot=title]").textContent = session["spacy.domain/title"];
  element.querySelector("[data-slot=sponsor]").textContent = sponsor;
  element.querySelector("[data-slot=description]").textContent = session["spacy.domain/description"];
  element.querySelector("[id=title]").setAttribute("id", "title" + id);
  element.setAttribute("aria-labelledby", "title" + id);

  element.querySelectorAll("[data-command]").forEach(cmd => {
    if (!actions.includes(cmd.getAttribute("data-command"))) {
      removeNode(cmd);
      return;
    }

    cmd.querySelector("input[name=id]").setAttribute("value", id);
  });

  if (currentUser() !== sponsor) {
    removeNode(element.querySelector("[is-sponsor]"));
  }

  if (parentWrapper) {
    const parent = document.createElement(parentWrapper);
    parent.appendChild(element);
    return parent;
  }

  return element;
}

export function extractSession(element) {
  return {
    "spacy.domain/sponsor": element.querySelector("[data-slot=sponsor]")?.textContent,
    "spacy.domain/session": {
      "spacy.domain/id": element.getAttribute("data-id") || element.querySelector("[data-id]").getAttribute("data-id"),
      "spacy.domain/title": element.querySelector("[data-slot=title]")?.textContent,
      "spacy.domain/description": element.querySelector("[data-slot=description]")?.textContent
    }
  }
}
