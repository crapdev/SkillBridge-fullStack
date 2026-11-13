"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.VerificationModule = void 0;
const common_1 = require("@nestjs/common");
const email_module_1 = require("../email/email.module");
const verification_mailer_1 = require("./verification-mailer");
const verification_mailer_port_1 = require("./verification-mailer.port");
const verification_support_controller_1 = require("./verification-support.controller");
const verification_support_service_1 = require("./verification-support.service");
let VerificationModule = class VerificationModule {
};
exports.VerificationModule = VerificationModule;
exports.VerificationModule = VerificationModule = __decorate([
    (0, common_1.Module)({
        imports: [email_module_1.EmailModule],
        controllers: [verification_support_controller_1.VerificationSupportController],
        providers: [
            verification_support_service_1.VerificationSupportService,
            verification_mailer_1.VerificationMailer,
            { provide: verification_mailer_port_1.VerificationMailerPort, useExisting: verification_mailer_1.VerificationMailer },
        ],
        exports: [verification_mailer_port_1.VerificationMailerPort],
    })
], VerificationModule);
//# sourceMappingURL=verification.module.js.map