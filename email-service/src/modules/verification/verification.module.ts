import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AppUser } from './entities/app-user.entity';
import { PendingRegistration } from './entities/pending-registration.entity';
import { VerificationResendRequest } from './entities/verification-resend-request.entity';
import { RegistrationController } from './registration.controller';
import { RegistrationService } from './registration.service';
import { StubVerificationMailer } from './stub-verification-mailer.adapter';
import { VerificationMailerPort } from './verification-mailer.port';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      PendingRegistration,
      VerificationResendRequest,
      AppUser
    ]),
    ConfigModule
  ],
  controllers: [RegistrationController],
  providers: [
    RegistrationService,
    {
      provide: VerificationMailerPort,
      useClass: StubVerificationMailer
    }
  ],
  exports: [TypeOrmModule, VerificationMailerPort, RegistrationService]
})
export class VerificationModule {}
