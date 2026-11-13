"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.EmailModule = void 0;
const common_1 = require("@nestjs/common");
const email_sender_port_1 = require("./email-sender.port");
const fake_email_sender_adapter_1 = require("./fake-email-sender.adapter");
let EmailModule = class EmailModule {
};
exports.EmailModule = EmailModule;
exports.EmailModule = EmailModule = __decorate([
    (0, common_1.Module)({
        providers: [
            fake_email_sender_adapter_1.FakeEmailSenderAdapter,
            { provide: email_sender_port_1.EmailSenderPort, useExisting: fake_email_sender_adapter_1.FakeEmailSenderAdapter }
        ],
        exports: [email_sender_port_1.EmailSenderPort]
    })
], EmailModule);
//# sourceMappingURL=email.module.js.map