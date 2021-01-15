export class FactHandler extends HTMLElement {
  connectedCallback () {
    this.eventSource = new EventSource(this.sseUri);
    this.eventSource.onmessage = this.handleFact.bind(this);

    window.addEventListener("beforeunload", () => {
      this.eventSource.close();
    });
  }

  handleFact(event) {
    const data = JSON.parse(event.data);
    const fact = data["spacy.domain/fact"];

    if (fact) {
      this.dispatchEvent(new CustomEvent(fact, { detail: data, bubbles: true }));
    }
  }

  get sseUri() {
    return this.getAttribute("uri");
  }
}
