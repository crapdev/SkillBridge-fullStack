"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.AppModule = void 0;
const common_1 = require("@nestjs/common");
const config_1 = require("@nestjs/config");
const Joi = require("joi");
const email_module_1 = require("./modules/email/email.module");
const booking_events_module_1 = require("./modules/booking-events/booking-events.module");
const health_module_1 = require("./modules/health/health.module");
const database_module_1 = require("./modules/database/database.module");
const verification_module_1 = require("./modules/verification/verification.module");
let AppModule = class AppModule {
};
exports.AppModule = AppModule;
exports.AppModule = AppModule = __decorate([
    (0, common_1.Module)({
        imports: [
            config_1.ConfigModule.forRoot({
                isGlobal: true,
                envFilePath: '.env',
                validationSchema: Joi.object({
                    NODE_ENV: Joi.string().valid('development', 'test', 'production').default('development'),
                    PORT: Joi.number().default(3000),
                    APP_NAME: Joi.string().default('skillbridge-email-service'),
                    RABBITMQ_URL: Joi.string().uri().optional(),
                    RABBITMQ_EXCHANGE: Joi.string().default('booking.events'),
                    RABBITMQ_EMAIL_QUEUE: Joi.string().default('booking.emails.queue'),
                    RABBITMQ_EMAIL_DLQ: Joi.string().default('booking.emails.dlq'),
                    MAIL_PROVIDER: Joi.string().valid('fake', 'mailpit', 'brevo').default('fake'),
                    MAIL_FROM: Joi.string().email().default('no-reply@skillbridge.local'),
                    MAILPIT_HOST: Joi.string().default('localhost'),
                    MAILPIT_PORT: Joi.number().default(1025),
                    BREVO_API_KEY: Joi.string().allow('').optional(),
                    FRONTEND_URL: Joi.string().uri().default('http://localhost:4200'),
                    DATABASE_URL: Joi.string().uri({ scheme: ['postgres', 'postgresql'] }).required()
                })
            }),
            email_module_1.EmailModule,
            booking_events_module_1.BookingEventsModule,
            health_module_1.HealthModule,
            database_module_1.DatabaseModule,
            verification_module_1.VerificationModule
        ]
    })
], AppModule);
//# sourceMappingURL=app.module.js.map