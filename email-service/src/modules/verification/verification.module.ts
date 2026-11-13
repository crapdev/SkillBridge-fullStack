import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { EmailModule } from '../email/email.module';
import { AppUser } from './entities/app-user.entity';
import { PendingRegistration } from './entities/pending-registration.entity';
import { VerificationResendRequest } from './entities/verification-resend-request.entity';
import { RegistrationController } from './registration.controller';
import { RegistrationService } from './registration.service';
import { VerificationMailer } from './verification.mailer';
import { VerificationMailerPort } from './verification-mailer.port';
import { VerificationSupportController } from './verification-support.controller';
import { VerificationSupportService } from './verification-support.service';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      PendingRegistration,
      VerificationResendRequest,
      AppUser
    ]),
    ConfigModule,
    EmailModule
  ],
  controllers: [RegistrationController, VerificationSupportController],
  providers: [
    RegistrationService,
    VerificationSupportService,
    {
      provide: VerificationMailerPort,
      useClass: VerificationMailer
    }
  ],
  exports: [TypeOrmModule, VerificationMailerPort, RegistrationService]
})
export class VerificationModule {}
