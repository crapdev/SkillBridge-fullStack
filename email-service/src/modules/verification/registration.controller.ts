import { Body, Controller, HttpCode, HttpStatus, Post, Res } from '@nestjs/common';
import { Response } from 'express';
import { RegisterDto } from './dto/register.dto';
import { VerifyDto } from './dto/verify.dto';
import { RegistrationService } from './registration.service';

@Controller('verification')
export class RegistrationController {
  constructor(private readonly registrationService: RegistrationService) {}

  @Post('register')
  @HttpCode(HttpStatus.ACCEPTED)
  async register(
    @Body() dto: RegisterDto
  ): Promise<{ email: string; message: string }> {
    return this.registrationService.register(dto, 'CUSTOMER');
  }

  @Post('register/provider')
  @HttpCode(HttpStatus.ACCEPTED)
  async registerProvider(
    @Body() dto: RegisterDto
  ): Promise<{ email: string; message: string }> {
    return this.registrationService.register(dto, 'PROVIDER');
  }

  @Post('verify')
  @HttpCode(HttpStatus.OK) // sin esto Nest responde 201 y el frontend distingue cliente (200) de proveedor (202)
  async verify(
    @Body() dto: VerifyDto,
    @Res({ passthrough: true }) res: Response
  ): Promise<{ email: string; name: string; role: string } | { status: string; message: string }> {
    return this.registrationService.verify(dto, res);
  }
}
