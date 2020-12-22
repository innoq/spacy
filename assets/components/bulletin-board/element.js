import { replaceNode } from "uitil/dom";

export class BulletinBoard extends HTMLElement {
  connectedCallback() {
    document.body.addEventListener("spacy.domain/session-scheduled", this.reloadBoard.bind(this));
    document.body.addEventListener("spacy.domain/session-deleted", this.reloadBoard.bind(this));
    document.body.addEventListener("spacy.ui/up-next", this.reloadBoard.bind(this));
  }

  reloadBoard() {
    if (this.hinclude) {
      this.hinclude.refresh();
      return;
    }

    replaceNode(this.schedule, this.newHInclude());
  }

  get schedule() {
    return this.querySelector(".schedule");
  }

  get hinclude() {
    return this.querySelector("h-include");
  }

  newHInclude() {
    return this.querySelector("template").content
      .querySelector("*") // Find first true HTML node which will be our session markup
      .cloneNode(true);
  }
}
