export function fillTemplate(template, slot, content) {
  const element = template.querySelector(`[data-slot="${slot}"]`)
  if (!element) {
    return;
  }
  element.textContent = content;
}
