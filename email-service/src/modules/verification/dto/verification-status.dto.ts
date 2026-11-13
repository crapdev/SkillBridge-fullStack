import { IsEmail, IsString, MaxLength } from 'class-validator';

export class VerificationStatusDto {
  @IsEmail()
  @MaxLength(180)
  email!: string;

  @IsString()
  @MaxLength(72)
  password!: string;
}
