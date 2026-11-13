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
var __param = (this && this.__param) || function (paramIndex, decorator) {
    return function (target, key) { decorator(target, key, paramIndex); }
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.VerificationSupportController = void 0;
const common_1 = require("@nestjs/common");
const resend_verification_dto_1 = require("./dto/resend-verification.dto");
const verification_status_dto_1 = require("./dto/verification-status.dto");
const verification_support_service_1 = require("./verification-support.service");
let VerificationSupportController = class VerificationSupportController {
    verificationSupportService;
    constructor(verificationSupportService) {
        this.verificationSupportService = verificationSupportService;
    }
    resend(dto) {
        return this.verificationSupportService.resend(dto.email);
    }
    status(dto) {
        return this.verificationSupportService.status(dto.email, dto.password);
    }
};
exports.VerificationSupportController = VerificationSupportController;
__decorate([
    (0, common_1.Post)('resend'),
    (0, common_1.HttpCode)(common_1.HttpStatus.ACCEPTED),
    __param(0, (0, common_1.Body)()),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [resend_verification_dto_1.ResendVerificationDto]),
    __metadata("design:returntype", Promise)
], VerificationSupportController.prototype, "resend", null);
__decorate([
    (0, common_1.Post)('status'),
    (0, common_1.HttpCode)(common_1.HttpStatus.OK),
    __param(0, (0, common_1.Body)()),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [verification_status_dto_1.VerificationStatusDto]),
    __metadata("design:returntype", Promise)
], VerificationSupportController.prototype, "status", null);
exports.VerificationSupportController = VerificationSupportController = __decorate([
    (0, common_1.Controller)('verification'),
    __metadata("design:paramtypes", [verification_support_service_1.VerificationSupportService])
], VerificationSupportController);
//# sourceMappingURL=verification-support.controller.js.map