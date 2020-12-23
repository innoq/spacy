import { fillTemplate } from "../util/template";
import { replaceNode } from "uitil/dom";

export class UpNext extends HTMLElement {
  connectedCallback() {
    document.body.addEventListener("spacy.ui/up-next", this.updateStatus.bind(this));
  }

  updateStatus(ev) {
    if (typeof ev.detail === 'string') {
      this.swapContent(this.statusMessage(ev.detail));
      this.setAttribute("up-next", "false");
      return;
    }

    const sponsor = ev.detail["spacy.domain/sponsor"];
    const session = ev.detail["spacy.domain/session"];

    const upNext = sponsor === this.currentUser;
    const status = upNext ? "spacy.ui/up-next" : "spacy.ui/please-wait";

    const template = this.statusMessage(status);
    fillTemplate(template, "title", session && session["spacy.domain/title"]);

    this.swapContent(template);
    this.setAttribute("up-next", upNext);
  }

  swapContent(content) {
    replaceNode(this.p, content);
  }

  get p() {
    return this.querySelector("p");
  }

  get currentUser() {
    return this.getAttribute("current-user");
  }

  statusMessage(status) {
    const template = this.querySelector(`template[data-template="${status}"]`);
    if (!template) {
      return;
    }
    return template.content.cloneNode(true);
  }
}
