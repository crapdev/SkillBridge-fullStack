"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __metadata = (this && this.__metadata) || function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function") return Reflect.metadata(k, v);
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.VerificationMailer = void 0;
const common_1 = require("@nestjs/common");
const config_1 = require("@nestjs/config");
const email_sender_port_1 = require("../email/email-sender.port");
const verification_email_template_1 = require("./templates/verification-email.template");
let VerificationMailer = class VerificationMailer {
    configService;
    emailSender;
    constructor(configService, emailSender) {
        this.configService = configService;
        this.emailSender = emailSender;
    }
    async sendVerification(mail) {
        const frontendUrl = this.configService.get('FRONTEND_URL', 'http://localhost:4200');
        const verifyUrl = `${frontendUrl.replace(/\/$/, '')}/verificar-correo?token=${encodeURIComponent(mail.token)}`;
        const template = (0, verification_email_template_1.buildVerificationEmailTemplate)({
            name: mail.name,
            verifyUrl,
            role: mail.role,
        });
        await this.emailSender.send({
            to: mail.to,
            recipientName: mail.name,
            subject: template.subject,
            html: template.html,
            text: template.text,
        });
    }
};
exports.VerificationMailer = VerificationMailer;
exports.VerificationMailer = VerificationMailer = __decorate([
    (0, common_1.Injectable)(),
    __metadata("design:paramtypes", [config_1.ConfigService,
        email_sender_port_1.EmailSenderPort])
], VerificationMailer);
//# sourceMappingURL=verification-mailer.js.map