import { ValidationPipe } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import * as cookieParser from 'cookie-parser';
import { AppModule } from './app.module';
import { ProblemDetailFilter } from './common/problem-detail.filter';

async function bootstrap(): Promise<void> {
  const app = await NestFactory.create(AppModule);
  app.setGlobalPrefix('api');
  app.use(cookieParser());
  app.useGlobalPipes(new ValidationPipe({ whitelist: true, transform: true, forbidNonWhitelisted: true }));
  app.useGlobalFilters(new ProblemDetailFilter());
  const port = Number(process.env.PORT ?? 3000);
  await app.listen(port);
  console.log(`Email service listening on port ${port}`);
}
void bootstrap();
