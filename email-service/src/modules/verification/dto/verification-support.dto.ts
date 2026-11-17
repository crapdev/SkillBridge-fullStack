import { IsEmail, IsString, MaxLength } from 'class-validator';

export class ResendDto {
  @IsEmail()
  @MaxLength(180)
  email: string;
}

export class StatusDto {
  @IsEmail()
  @MaxLength(180)
  email: string;

  @IsString()
  @MaxLength(72)
  password: string;
}
