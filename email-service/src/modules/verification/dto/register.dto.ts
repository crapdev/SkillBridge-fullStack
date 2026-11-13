import { IsEmail, IsNotEmpty, IsString, MaxLength, MinLength } from 'class-validator';

export class RegisterDto {
  @IsString()
  @IsNotEmpty()
  @MaxLength(120)
  name: string;

  @IsEmail()
  @MaxLength(180)
  email: string;

  @IsString()
  @MinLength(8)
  @MaxLength(72)
  password: string;
}
