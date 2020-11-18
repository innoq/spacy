function fillTemplate(template, slot, content) {
  const element = template.querySelector(`[data-slot="${slot}"]`)
  if (!element) {
    return;
  }
  element.textContent = content;
}

export class UserNotifications extends HTMLElement {
  connectedCallback() {
    if (!this.entries) {
      return;
    }
    this.hidden = false;

    document.body.addEventListener("spacy.domain/session-suggested", this.addNotification.bind(this));
    document.body.addEventListener("spacy.domain/session-scheduled", this.addNotification.bind(this));
  }

  addNotification(ev) {
    const sponsor = ev.detail["spacy.domain/sponsor"];
    const session = ev.detail["spacy.domain/session"];
    const notification = this.newNotification(ev.type);

    if (!session || !this.notifyFor(ev.type, sponsor) || !notification) {
      return; // Add no notifications for things we don't understand
    }

    fillTemplate(notification, "title", session["spacy.domain/title"]);
    fillTemplate(notification, "sponsor", sponsor);
    fillTemplate(notification, "at", new Date()); // TODO: use Crux timestamp, but deal with timezones
    fillTemplate(notification, "room", ev.detail["spacy.domain/room"]);
    fillTemplate(notification, "time", ev.detail["spacy.domain/time"]);

    this.entries.appendChild(notification);
  }

  get entries() {
    return this.querySelector("ul");
  }

  get currentUser() {
    return this.getAttribute("current-user");
  }

  notifyFor(fact, sponsor) {
    switch (fact) {
      case "spacy.domain/session-scheduled":
        return true;
      default:
        return sponsor === this.currentUser;
    }
  }

  newNotification(fact) {
    const template = this.querySelector(`template[data-template="${fact}"]`);
    if (!template) {
      return;
    }
    return template.content
      .querySelector("*") // Find first true HTML node which will be our session markup
      .cloneNode(true);
  }
}
