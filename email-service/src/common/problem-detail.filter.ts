import {
  ArgumentsHost,
  Catch,
  ExceptionFilter,
  HttpException,
  HttpStatus
} from '@nestjs/common';
import { Response } from 'express';

export interface ProblemDetailResponse {
  status: number;
  title: string;
  detail: string;
  reason?: string;
  [key: string]: unknown;
}

@Catch()
export class ProblemDetailFilter implements ExceptionFilter {
  catch(exception: unknown, host: ArgumentsHost): void {
    const ctx = host.switchToHttp();
    const response = ctx.getResponse<Response>();

    let status = HttpStatus.INTERNAL_SERVER_ERROR;
    let title = 'Internal Server Error';
    let detail = 'Ocurrió un error inesperado';
    let reason: string | undefined;

    if (exception instanceof HttpException) {
      status = exception.getStatus();
      title = this.getDefaultTitle(status);
      const res = exception.getResponse();

      if (typeof res === 'string') {
        detail = res;
      } else if (typeof res === 'object' && res !== null) {
        const obj = res as Record<string, any>;
        if (Array.isArray(obj.message)) {
          detail = 'Revisa los datos del formulario.';
          title = 'Bad Request';
        } else if (typeof obj.detail === 'string') {
          detail = obj.detail;
        } else if (typeof obj.message === 'string') {
          detail = obj.message;
        }

        if (obj.title && typeof obj.title === 'string') {
          title = obj.title;
        }
        if (obj.reason && typeof obj.reason === 'string') {
          reason = obj.reason;
        }
      }
    } else if (exception instanceof Error) {
      detail = exception.message;
    }

    const payload: ProblemDetailResponse = {
      status,
      title,
      detail,
      ...(reason ? { reason } : {})
    };

    response
      .status(status)
      .contentType('application/problem+json')
      .json(payload);
  }

  private getDefaultTitle(status: number): string {
    switch (status) {
      case 400:
        return 'Bad Request';
      case 401:
        return 'Unauthorized';
      case 403:
        return 'Forbidden';
      case 404:
        return 'Not Found';
      case 409:
        return 'Conflict';
      case 422:
        return 'Unprocessable Entity';
      case 429:
        return 'Too Many Requests';
      case 500:
        return 'Internal Server Error';
      default:
        return 'Error';
    }
  }
}
