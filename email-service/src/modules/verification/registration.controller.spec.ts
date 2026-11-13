import { Test, TestingModule } from '@nestjs/testing';
import { Response } from 'express';
import { RegistrationController } from './registration.controller';
import { RegistrationService } from './registration.service';

describe('RegistrationController', () => {
  let controller: RegistrationController;
  let service: any;

  beforeEach(async () => {
    service = {
      register: jest.fn(),
      verify: jest.fn()
    };

    const module: TestingModule = await Test.createTestingModule({
      controllers: [RegistrationController],
      providers: [{ provide: RegistrationService, useValue: service }]
    }).compile();

    controller = module.get<RegistrationController>(RegistrationController);
  });

  it('should delegate register to service with CUSTOMER role', async () => {
    const dto = { name: 'Ada', email: 'ada@example.com', password: 'Password123' };
    service.register.mockResolvedValue({ email: 'ada@example.com', message: 'ok' });

    const result = await controller.register(dto);
    expect(service.register).toHaveBeenCalledWith(dto, 'CUSTOMER');
    expect(result).toEqual({ email: 'ada@example.com', message: 'ok' });
  });

  it('should delegate registerProvider to service with PROVIDER role', async () => {
    const dto = { name: 'Ada', email: 'ada@example.com', password: 'Password123' };
    service.register.mockResolvedValue({ email: 'ada@example.com', message: 'ok' });

    const result = await controller.registerProvider(dto);
    expect(service.register).toHaveBeenCalledWith(dto, 'PROVIDER');
    expect(result).toEqual({ email: 'ada@example.com', message: 'ok' });
  });

  it('should delegate verify to service with dto and response', async () => {
    const dto = { token: 'tok-123' };
    const res = {} as Response;
    service.verify.mockResolvedValue({ email: 'ada@example.com', name: 'Ada', role: 'CUSTOMER' });

    const result = await controller.verify(dto, res);
    expect(service.verify).toHaveBeenCalledWith(dto, res);
    expect(result).toEqual({ email: 'ada@example.com', name: 'Ada', role: 'CUSTOMER' });
  });
});
