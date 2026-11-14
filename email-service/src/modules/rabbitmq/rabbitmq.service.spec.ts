import { ConfigService } from '@nestjs/config';
import { connect } from 'amqp-connection-manager';
import { describeBroker, RabbitMqService } from './rabbitmq.service';

jest.mock('amqp-connection-manager', () => ({ connect: jest.fn() }));

function fakeConnection() {
  return { on: jest.fn(), createChannel: jest.fn().mockReturnValue({}), close: jest.fn().mockResolvedValue(undefined) };
}

const configWith = (url?: string) => ({ get: () => url }) as unknown as ConfigService;

describe('RabbitMqService', () => {
  beforeEach(() => jest.mocked(connect).mockReset());

  it('sin RABBITMQ_URL queda desactivado y no abre conexión', async () => {
    const service = new RabbitMqService(configWith(undefined));
    expect(service.isEnabled()).toBe(false);
    expect(() => service.createChannel({})).toThrow('RABBITMQ_URL');
    await service.onApplicationShutdown();
    expect(connect).not.toHaveBeenCalled();
  });

  it('una URL vacía cuenta como no configurada', () => {
    expect(new RabbitMqService(configWith('')).isEnabled()).toBe(false);
  });

  it('reutiliza una única conexión para todos los canales y la cierra al apagar', async () => {
    const conn = fakeConnection();
    jest.mocked(connect).mockReturnValue(conn as never);
    const service = new RabbitMqService(configWith('amqp://guest:guest@localhost:5672'));

    service.createChannel({ name: 'a' });
    service.createChannel({ name: 'b' });
    await service.onApplicationShutdown();

    expect(connect).toHaveBeenCalledTimes(1);
    expect(conn.createChannel).toHaveBeenCalledTimes(2);
    expect(conn.close).toHaveBeenCalledTimes(1);
  });

  it('registra conexión y desconexión sin exponer credenciales', () => {
    const conn = fakeConnection();
    jest.mocked(connect).mockReturnValue(conn as never);
    const service = new RabbitMqService(configWith('amqp://guest:secreto@rabbitmq:5672'));
    service.createChannel({});
    expect(conn.on.mock.calls.map(([event]) => event)).toEqual(['connect', 'disconnect', 'connectFailed']);
  });
});

describe('describeBroker', () => {
  it('omite usuario y contraseña', () => {
    expect(describeBroker('amqp://guest:secreto@rabbitmq:5672')).toBe('rabbitmq:5672');
    expect(describeBroker('amqp://u:p@host/vhost')).toBe('host:5672/vhost');
    expect(describeBroker('no es url')).toBe('url-invalida');
  });
});
