import { Controller, Get } from '@nestjs/common';

@Controller('health')
export class HealthController {
  @Get()
  check(): { status: string; service: string } {
    return { status: 'ok', service: process.env.APP_NAME ?? 'skillbridge-email-service' };
  }
}
