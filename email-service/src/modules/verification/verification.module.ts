import { Module } from '@nestjs/common';
import { EmailModule } from '../email/email.module';
import { VerificationMailer } from './verification-mailer';
import { VerificationMailerPort } from './verification-mailer.port';
import { VerificationSupportController } from './verification-support.controller';
import { VerificationSupportService } from './verification-support.service';

@Module({
  imports: [EmailModule],
  controllers: [VerificationSupportController],
  providers: [
    VerificationSupportService,
    VerificationMailer,
    { provide: VerificationMailerPort, useExisting: VerificationMailer },
  ],
  exports: [VerificationMailerPort],
})
export class VerificationModule {}
