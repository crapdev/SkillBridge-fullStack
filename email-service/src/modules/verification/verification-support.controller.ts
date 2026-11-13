import { Body, Controller, HttpCode, HttpStatus, Post } from '@nestjs/common';
import { ResendVerificationDto } from './dto/resend-verification.dto';
import { VerificationStatusDto } from './dto/verification-status.dto';
import { VerificationSupportService } from './verification-support.service';

@Controller('verification')
export class VerificationSupportController {
  constructor(private readonly verificationSupportService: VerificationSupportService) {}

  @Post('resend')
  @HttpCode(HttpStatus.ACCEPTED)
  resend(@Body() dto: ResendVerificationDto): Promise<{ message: string }> {
    return this.verificationSupportService.resend(dto.email);
  }

  @Post('status')
  @HttpCode(HttpStatus.OK)
  status(@Body() dto: VerificationStatusDto): Promise<{ pending: boolean }> {
    return this.verificationSupportService.status(dto.email, dto.password);
  }
}
