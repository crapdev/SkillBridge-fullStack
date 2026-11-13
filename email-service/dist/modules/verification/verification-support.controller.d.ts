import { ResendVerificationDto } from './dto/resend-verification.dto';
import { VerificationStatusDto } from './dto/verification-status.dto';
import { VerificationSupportService } from './verification-support.service';
export declare class VerificationSupportController {
    private readonly verificationSupportService;
    constructor(verificationSupportService: VerificationSupportService);
    resend(dto: ResendVerificationDto): Promise<{
        message: string;
    }>;
    status(dto: VerificationStatusDto): Promise<{
        pending: boolean;
    }>;
}
