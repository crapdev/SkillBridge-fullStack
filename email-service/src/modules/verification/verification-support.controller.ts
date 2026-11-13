import { Body, Controller, HttpCode, HttpStatus, Post } from '@nestjs/common';
import { ResendDto, StatusDto } from './dto/verification-support.dto';
import { VerificationSupportService } from './verification-support.service';

@Controller('verification')
export class VerificationSupportController {
  constructor(private readonly support: VerificationSupportService) {}

  @Post('resend')
  @HttpCode(HttpStatus.ACCEPTED)
  resend(@Body() dto: ResendDto): Promise<{ message: string }> {
    return this.support.resend(dto.email);
  }

  // POST y no GET: la contraseña no debe viajar en la URL (quedaría en logs e historial)
  @Post('status')
  @HttpCode(HttpStatus.OK)
  status(@Body() dto: StatusDto): Promise<{ pending: boolean }> {
    return this.support.status(dto.email, dto.password);
  }
}
