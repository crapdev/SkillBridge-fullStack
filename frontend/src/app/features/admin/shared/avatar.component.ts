import { Component, computed, input } from '@angular/core';
import { avatarTone, initials } from './format';

/** Avatar con iniciales; no hay fotos de perfil en la base de datos. */
@Component({
  selector: 'adm-avatar',
  standalone: true,
  template: `<span class="adm-avatar" [class.muted]="muted()" [style.width.px]="size()" [style.height.px]="size()"
    [style.font-size.px]="size() * 0.38" [style.background]="tone().bg" [style.color]="tone().fg"
    aria-hidden="true">{{ letters() }}</span>`,
  styles: [`
    :host{display:inline-flex;flex:none}
    .adm-avatar{display:inline-grid;place-items:center;border-radius:50%;font-weight:700;letter-spacing:.02em}
    .adm-avatar.muted{filter:grayscale(1);opacity:.6}
  `]
})
export class AvatarComponent {
  readonly name = input.required<string>();
  readonly size = input(36);
  readonly muted = input(false);
  protected readonly letters = computed(() => initials(this.name()));
  protected readonly tone = computed(() => avatarTone(this.name()));
}
