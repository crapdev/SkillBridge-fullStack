import { Injectable, Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { Channel } from 'amqplib';
import { AmqpConnectionManager, ChannelWrapper, connect } from 'amqp-connection-manager';
import { BookingEventsConsumer } from './booking-events.consumer';
import {
  BOOKING_EMAIL_ROUTING_KEYS,
  BOOKING_EMAILS_DLQ,
  BOOKING_EMAILS_DLQ_KEY,
  BOOKING_EMAILS_DLX,
  BOOKING_EMAILS_QUEUE,
  BOOKING_EVENTS_EXCHANGE,
  BOOKING_EVENTS_EXCHANGE_TYPE,
} from './booking-events.constants';

/**
 * Conexión a RabbitMQ y topología de la cola de correos de reservas.
 * No se usa @nestjs/microservices: espera mensajes con formato Nest y Java publica JSON plano.
 */
@Injectable()
export class RabbitMqService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(RabbitMqService.name);
  private connection?: AmqpConnectionManager;
  private channel?: ChannelWrapper;

  constructor(
    private readonly config: ConfigService,
    private readonly consumer: BookingEventsConsumer,
  ) {}

  onModuleInit(): void {
    const url = this.config.get<string>('RABBITMQ_URL');
    if (!url) {
      this.logger.warn('RABBITMQ_URL no configurada: no se consumen eventos de reservas');
      return;
    }

    // Reconecta solo; si RabbitMQ no está, el servicio arranca igual (la verificación de correo no depende de él)
    this.connection = connect([url]);
    this.connection.on('connect', () => this.logger.log('Conectado a RabbitMQ'));
    this.connection.on('disconnect', ({ err }) => this.logger.warn(`RabbitMQ desconectado: ${err?.message ?? 'sin detalle'}`));

    // Todo va en el setup: al reconectar se vuelve a declarar la topología y a registrar el consumidor
    this.channel = this.connection.createChannel({
      json: false,
      setup: async (channel: Channel) => {
        // Mismos parámetros que Java (TopicExchange durable, sin autoDelete); si no, RabbitMQ responde 406
        await channel.assertExchange(BOOKING_EVENTS_EXCHANGE, BOOKING_EVENTS_EXCHANGE_TYPE, {
          durable: true,
          autoDelete: false,
        });
        await channel.assertExchange(BOOKING_EMAILS_DLX, 'direct', { durable: true, autoDelete: false });
        await channel.assertQueue(BOOKING_EMAILS_DLQ, { durable: true });
        await channel.bindQueue(BOOKING_EMAILS_DLQ, BOOKING_EMAILS_DLX, BOOKING_EMAILS_DLQ_KEY);

        await channel.assertQueue(BOOKING_EMAILS_QUEUE, {
          durable: true,
          deadLetterExchange: BOOKING_EMAILS_DLX,
          deadLetterRoutingKey: BOOKING_EMAILS_DLQ_KEY,
        });
        for (const key of BOOKING_EMAIL_ROUTING_KEYS) {
          await channel.bindQueue(BOOKING_EMAILS_QUEUE, BOOKING_EVENTS_EXCHANGE, key);
        }

        await channel.prefetch(10);
        await channel.consume(
          BOOKING_EMAILS_QUEUE,
          (message) => {
            if (message) void this.consumer.handle(message, channel);
          },
          { noAck: false },
        );
      },
    });
  }

  async onModuleDestroy(): Promise<void> {
    await this.channel?.close();
    await this.connection?.close();
  }
}
