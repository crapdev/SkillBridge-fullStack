export interface VerificationEmailTemplateData {
    name: string;
    verifyUrl: string;
    role: 'CUSTOMER' | 'PROVIDER';
}
export interface EmailTemplate {
    subject: string;
    html: string;
    text: string;
}
export declare function buildVerificationEmailTemplate(data: VerificationEmailTemplateData): EmailTemplate;
