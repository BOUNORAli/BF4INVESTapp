import React from 'react';
export * from 'lucide-react';

interface LogoProps extends React.SVGProps<SVGSVGElement> {
  light?: boolean;
  className?: string;
}

// Logo vectoriel simplifié BF4 INVEST pour le site vitrine
export const Logo: React.FC<LogoProps> = ({ light = false, className, ...props }) => (
  <svg
    viewBox="0 0 160 60"
    xmlns="http://www.w3.org/2000/svg"
    className={className}
    {...props}
  >
    {/* Ellipse externe */}
    <ellipse
      cx="80"
      cy="30"
      rx="75"
      ry="22"
      fill="none"
      stroke={light ? '#ffffff' : '#0070c9'}
      strokeWidth="4"
    />
    {/* Ellipse interne */}
    <ellipse
      cx="80"
      cy="30"
      rx="55"
      ry="16"
      fill={light ? '#ffffff' : '#ffffff'}
      stroke="none"
    />
    {/* Texte BF4 INVEST */}
    <text
      x="80"
      y="34"
      textAnchor="middle"
      fontFamily="Manrope, system-ui, sans-serif"
      fontWeight="700"
      fontSize="16"
      fill="#0070c9"
    >
      BF4 INVEST
    </text>
  </svg>
);

