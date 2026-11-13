import { HttpException, HttpStatus } from '@nestjs/common';

export class ProblemException extends HttpException {
  readonly reason?: string;

  constructor(status: number | HttpStatus, detail: string, reason?: string, title?: string) {
    super(
      {
        status,
        title: title ?? ProblemException.defaultTitle(status),
        detail,
        ...(reason ? { reason } : {})
      },
      status
    );
    this.reason = reason;
  }

  private static defaultTitle(status: number): string {
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
