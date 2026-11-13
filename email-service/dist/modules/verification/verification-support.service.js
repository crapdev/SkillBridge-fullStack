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
var VerificationSupportService_1;
Object.defineProperty(exports, "__esModule", { value: true });
exports.VerificationSupportService = void 0;
const common_1 = require("@nestjs/common");
const typeorm_1 = require("@nestjs/typeorm");
const typeorm_2 = require("typeorm");
const bcrypt = require("bcryptjs");
const verification_mailer_port_1 = require("./verification-mailer.port");
const token_util_1 = require("./token.util");
const RESEND_LIMIT = 3;
const RESEND_WINDOW_MINUTES = 15;
const VERIFICATION_TOKEN_TTL_HOURS = 24;
const DUMMY_PASSWORD = 'skillbridge-dummy-password-never-used';
let VerificationSupportService = VerificationSupportService_1 = class VerificationSupportService {
    dataSource;
    verificationMailer;
    logger = new common_1.Logger(VerificationSupportService_1.name);
    dummyPasswordHash = bcrypt.hash(DUMMY_PASSWORD, 10);
    constructor(dataSource, verificationMailer) {
        this.dataSource = dataSource;
        this.verificationMailer = verificationMailer;
    }
    async resend(rawEmail) {
        const email = this.normalizeEmail(rawEmail);
        const pending = await this.dataSource.transaction(async (manager) => {
            await manager.query('SELECT pg_advisory_xact_lock(hashtext($1))', [email]);
            const countRows = await manager.query(`SELECT COUNT(*)::int AS count
         FROM email.verification_resend_requests
         WHERE email = $1
           AND requested_at > NOW() - INTERVAL '${RESEND_WINDOW_MINUTES} minutes'`, [email]);
            const recentRequests = Number(countRows[0]?.count ?? 0);
            if (recentRequests >= RESEND_LIMIT) {
                throw new common_1.HttpException({
                    status: common_1.HttpStatus.TOO_MANY_REQUESTS,
                    title: 'Too Many Requests',
                    detail: 'Pediste demasiados enlaces. Espera unos minutos e inténtalo de nuevo.',
                }, common_1.HttpStatus.TOO_MANY_REQUESTS);
            }
            await manager.query(`INSERT INTO email.verification_resend_requests (email, requested_at)
         VALUES ($1, NOW())`, [email]);
            const rows = (await manager.query(`SELECT id, name, password_hash, role
         FROM email.pending_registrations
         WHERE email = $1 AND verified_at IS NULL
         LIMIT 1`, [email]));
            const registration = rows[0];
            if (!registration)
                return null;
            const token = (0, token_util_1.newVerificationToken)();
            await manager.query(`UPDATE email.pending_registrations
         SET token_hash = $2,
             token_expires_at = NOW() + INTERVAL '${VERIFICATION_TOKEN_TTL_HOURS} hours',
             updated_at = NOW()
         WHERE id = $1`, [registration.id, (0, token_util_1.hashToken)(token)]);
            return {
                name: registration.name,
                role: registration.role,
                token,
            };
        });
        if (pending) {
            try {
                await this.verificationMailer.sendVerification({
                    to: email,
                    name: pending.name,
                    token: pending.token,
                    role: pending.role,
                });
            }
            catch {
                this.logger.error('Verification email was not accepted by the email provider.');
            }
        }
        return {
            message: 'Si ese correo tiene un registro pendiente, te enviamos un enlace nuevo.',
        };
    }
    async status(rawEmail, password) {
        const email = this.normalizeEmail(rawEmail);
        const rows = (await this.dataSource.query(`SELECT password_hash
       FROM email.pending_registrations
       WHERE email = $1 AND verified_at IS NULL
       LIMIT 1`, [email]));
        const passwordHash = rows[0]?.password_hash ?? (await this.dummyPasswordHash);
        const passwordMatches = await bcrypt.compare(password, passwordHash);
        return { pending: Boolean(rows[0]) && passwordMatches };
    }
    normalizeEmail(email) {
        return email.trim().toLowerCase();
    }
};
exports.VerificationSupportService = VerificationSupportService;
exports.VerificationSupportService = VerificationSupportService = VerificationSupportService_1 = __decorate([
    (0, common_1.Injectable)(),
    __param(0, (0, typeorm_1.InjectDataSource)()),
    __metadata("design:paramtypes", [typeorm_2.DataSource,
        verification_mailer_port_1.VerificationMailerPort])
], VerificationSupportService);
//# sourceMappingURL=verification-support.service.js.map