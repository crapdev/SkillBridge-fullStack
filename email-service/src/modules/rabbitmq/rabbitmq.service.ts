import { Injectable, Logger, OnApplicationShutdown } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { connect } from 'amqp-connection-manager';
import type { AmqpConnectionManager, ChannelWrapper, CreateChannelOpts } from 'amqp-connection-manager';

/**
 * Única conexión del servicio con RabbitMQ. amqp-connection-manager reconecta solo y, al reconectar, vuelve a
 * ejecutar el setup de cada canal (topología, prefetch y consumidores). Si RABBITMQ_URL no está configurada el
 * servicio arranca igualmente (health, verificación...) pero sin consumir eventos.
 */
@Injectable()
export class RabbitMqService implements OnApplicationShutdown {
  private readonly logger = new Logger(RabbitMqService.name);
  private readonly url?: string;
  private connection?: AmqpConnectionManager;

  constructor(config: ConfigService) {
    this.url = config.get<string>('RABBITMQ_URL') || undefined;
    if (!this.url) {
      this.logger.warn('RABBITMQ_URL no está configurada: el consumo de eventos de reservas queda desactivado');
    }
  }

  isEnabled(): boolean {
    return this.url !== undefined;
  }

  /** Crea un canal sobre la conexión compartida (la abre la primera vez). */
  createChannel(opts: CreateChannelOpts): ChannelWrapper {
    return this.getConnection().createChannel(opts);
  }

  async onApplicationShutdown(): Promise<void> {
    if (this.connection) {
      await this.connection.close();
      this.logger.log('Conexión con RabbitMQ cerrada');
    }
  }

  private getConnection(): AmqpConnectionManager {
    if (!this.url) {
      throw new Error('RabbitMQ no está configurado (RABBITMQ_URL)');
    }
    if (!this.connection) {
      const broker = describeBroker(this.url);
      this.connection = connect([this.url], { heartbeatIntervalInSeconds: 10, reconnectTimeInSeconds: 5 });
      this.connection.on('connect', () => this.logger.log(`Conectado a RabbitMQ (${broker})`));
      this.connection.on('disconnect', ({ err }: { err?: Error }) =>
        this.logger.warn(`Desconectado de RabbitMQ (${broker}): ${err?.message ?? 'sin detalle'}; reintentando`));
      this.connection.on('connectFailed', ({ err }: { err?: Error }) =>
        this.logger.warn(`No se pudo conectar a RabbitMQ (${broker}): ${err?.message ?? 'sin detalle'}`));
    }
    return this.connection;
  }
}

/** host:puerto/vhost, sin usuario ni contraseña: la URL completa nunca va al log. */
export function describeBroker(url: string): string {
  try {
    const parsed = new URL(url);
    return `${parsed.hostname}:${parsed.port || '5672'}${parsed.pathname && parsed.pathname !== '/' ? parsed.pathname : ''}`;
  } catch {
    return 'url-invalida';
  }
}
