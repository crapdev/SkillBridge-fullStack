import { BadRequestException, HttpStatus } from '@nestjs/common';
import { ProblemDetailFilter } from './problem-detail.filter';
import { ProblemException } from './problem.exception';

describe('ProblemDetailFilter', () => {
  let filter: ProblemDetailFilter;
  let mockResponse: any;
  let mockHost: any;

  beforeEach(() => {
    filter = new ProblemDetailFilter();
    mockResponse = {
      status: jest.fn().mockReturnThis(),
      contentType: jest.fn().mockReturnThis(),
      json: jest.fn().mockReturnThis()
    };
    mockHost = {
      switchToHttp: () => ({
        getResponse: () => mockResponse
      })
    };
  });

  it('should format ProblemException with reason and status', () => {
    const exception = new ProblemException(
      HttpStatus.BAD_REQUEST,
      'El enlace de verificación venció.',
      'TOKEN_EXPIRED'
    );

    filter.catch(exception, mockHost);

    expect(mockResponse.status).toHaveBeenCalledWith(400);
    expect(mockResponse.contentType).toHaveBeenCalledWith('application/problem+json');
    expect(mockResponse.json).toHaveBeenCalledWith({
      status: 400,
      title: 'Bad Request',
      detail: 'El enlace de verificación venció.',
      reason: 'TOKEN_EXPIRED'
    });
  });

  it('should format ValidationPipe errors as "Revisa los datos del formulario."', () => {
    const exception = new BadRequestException(['name should not be empty', 'email must be an email']);

    filter.catch(exception, mockHost);

    expect(mockResponse.status).toHaveBeenCalledWith(400);
    expect(mockResponse.contentType).toHaveBeenCalledWith('application/problem+json');
    expect(mockResponse.json).toHaveBeenCalledWith({
      status: 400,
      title: 'Bad Request',
      detail: 'Revisa los datos del formulario.'
    });
  });

  it('should format generic HttpException', () => {
    const exception = new ProblemException(HttpStatus.UNPROCESSABLE_ENTITY, 'El correo ya está registrado');

    filter.catch(exception, mockHost);

    expect(mockResponse.status).toHaveBeenCalledWith(422);
    expect(mockResponse.contentType).toHaveBeenCalledWith('application/problem+json');
    expect(mockResponse.json).toHaveBeenCalledWith({
      status: 422,
      title: 'Unprocessable Entity',
      detail: 'El correo ya está registrado'
    });
  });
});
